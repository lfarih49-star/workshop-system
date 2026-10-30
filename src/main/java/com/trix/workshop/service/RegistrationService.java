package com.trix.workshop.service;

import com.trix.workshop.dto.RegistrationDto;
import com.trix.workshop.entity.Registration;
import com.trix.workshop.entity.User;
import com.trix.workshop.entity.Workshop;
import com.trix.workshop.exception.BusinessRuleException;
import com.trix.workshop.exception.ResourceNotFoundException;
import com.trix.workshop.exception.UnauthorizedException;
import com.trix.workshop.repository.AttendanceRepository;
import com.trix.workshop.repository.RegistrationRepository;
import com.trix.workshop.repository.WorkshopRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;

@Service
public class RegistrationService {
    private final RegistrationRepository registrationRepository;
    private final WorkshopRepository workshopRepository;
    private final AttendanceRepository attendanceRepository;
    private final AuditService auditService;

    public RegistrationService(RegistrationRepository registrationRepository,
                               WorkshopRepository workshopRepository,
                               AttendanceRepository attendanceRepository,
                               AuditService auditService) {
        this.registrationRepository = registrationRepository;
        this.workshopRepository = workshopRepository;
        this.attendanceRepository = attendanceRepository;
        this.auditService = auditService;
    }

    @Transactional
    public RegistrationDto registerParticipant(Long workshopId, User participant, String notes) {
        Workshop workshop = workshopRepository.findById(workshopId)
                .orElseThrow(() -> new ResourceNotFoundException("Workshop not found with ID: " + workshopId));

        if ("CANCELLED".equalsIgnoreCase(workshop.getStatus())) {
            throw new BusinessRuleException("Registration is closed. This workshop has been cancelled.");
        }

        if (LocalDate.now().isAfter(workshop.getRegistrationDeadline())) {
            throw new BusinessRuleException("Registration deadline (" + workshop.getRegistrationDeadline() + ") has already passed.");
        }

        if (registrationRepository.existsByParticipantAndWorkshop(participant.getUserId(), workshopId)) {
            throw new BusinessRuleException("You have already registered for this workshop.");
        }

        int registeredCount = workshopRepository.getRegisteredCount(workshopId);
        if (registeredCount >= workshop.getCapacity()) {
            throw new BusinessRuleException("This workshop has reached maximum capacity (" + workshop.getCapacity() + " seats). Registration is closed.");
        }

        Registration reg = new Registration();
        reg.setParticipantId(participant.getUserId());
        reg.setWorkshopId(workshopId);
        reg.setRegistrationStatus("APPROVED"); // Default auto-approved for valid seats
        reg.setNotes(notes != null ? notes.trim() : "Online self-registration");

        Registration savedReg = registrationRepository.save(reg);

        Long regId = savedReg.getRegistrationId();
        if (regId == null) {
            regId = registrationRepository.findByParticipantAndWorkshop(participant.getUserId(), workshopId)
                    .map(Registration::getRegistrationId)
                    .orElse(null);
            savedReg.setRegistrationId(regId);
        }

        final Long finalRegId = regId;

        // Auto-initialize attendance placeholder as NOT_RECORDED
        if (finalRegId != null) {
            try {
                attendanceRepository.saveOrUpdate(finalRegId, workshopId, participant.getUserId(), "NOT_RECORDED", "SYSTEM_INIT");
            } catch (Exception ignored) {}
        }

        auditService.log(participant.getEmail(), "REGISTER_WORKSHOP", "REGISTRATION", finalRegId,
                "Participant registered for workshop: " + workshop.getTitle());

        List<RegistrationDto> dtoList = registrationRepository.findByParticipantId(participant.getUserId());
        RegistrationDto resultDto = null;
        if (finalRegId != null && dtoList != null) {
            resultDto = dtoList.stream()
                    .filter(d -> finalRegId.equals(d.getRegistrationId()))
                    .findFirst()
                    .orElse(null);
        }
        if (resultDto == null && dtoList != null && !dtoList.isEmpty()) {
            resultDto = dtoList.get(0);
        }
        if (resultDto == null) {
            resultDto = new RegistrationDto();
            resultDto.setRegistrationId(finalRegId);
            resultDto.setParticipantId(participant.getUserId());
            resultDto.setParticipantName(participant.getFullName());
            resultDto.setParticipantEmail(participant.getEmail());
            resultDto.setParticipantPhone(participant.getPhone());
            resultDto.setParticipantDepartment(participant.getDepartment());
            resultDto.setParticipantInstitution(participant.getInstitution());
            resultDto.setWorkshopId(workshop.getWorkshopId());
            resultDto.setWorkshopTitle(workshop.getTitle());
            resultDto.setWorkshopCategory(workshop.getCategory());
            resultDto.setWorkshopDate(workshop.getWorkshopDate() != null ? workshop.getWorkshopDate().toString() : "");
            resultDto.setWorkshopVenue(workshop.getVenue());
            resultDto.setRegistrationDate(java.time.LocalDateTime.now());
            resultDto.setRegistrationStatus(savedReg.getRegistrationStatus() != null ? savedReg.getRegistrationStatus() : "APPROVED");
            resultDto.setNotes(savedReg.getNotes());
            resultDto.setAttendanceStatus("NOT_RECORDED");
        }
        return resultDto;
    }

    public List<RegistrationDto> getMyRegistrations(Long participantId) {
        return registrationRepository.findByParticipantId(participantId);
    }

    public List<RegistrationDto> getAllRegistrations(Long workshopId, String status, String search) {
        return registrationRepository.findAll(workshopId, status, search);
    }

    @Transactional
    public void updateRegistrationStatus(Long registrationId, String newStatus, User admin) {
        Registration reg = registrationRepository.findById(registrationId)
                .orElseThrow(() -> new ResourceNotFoundException("Registration not found with ID: " + registrationId));

        String upper = newStatus.toUpperCase().trim();
        if (!List.of("APPROVED", "REJECTED", "CANCELLED", "PENDING").contains(upper)) {
            throw new BusinessRuleException("Invalid registration status: " + newStatus);
        }

        registrationRepository.updateStatus(registrationId, upper);

        auditService.log(admin.getEmail(), "UPDATE_REGISTRATION_STATUS", "REGISTRATION", registrationId,
                "Status changed from " + reg.getRegistrationStatus() + " to " + upper);
    }

    @Transactional
    public void cancelRegistration(Long registrationId, User participant) {
        Registration reg = registrationRepository.findById(registrationId)
                .orElseThrow(() -> new ResourceNotFoundException("Registration not found with ID: " + registrationId));

        if (!reg.getParticipantId().equals(participant.getUserId())) {
            throw new UnauthorizedException("You can only cancel your own registration.");
        }

        if ("CANCELLED".equalsIgnoreCase(reg.getRegistrationStatus())) {
            throw new BusinessRuleException("This registration is already cancelled.");
        }

        registrationRepository.updateStatus(registrationId, "CANCELLED");

        auditService.log(participant.getEmail(), "CANCEL_REGISTRATION", "REGISTRATION", registrationId,
                "Participant cancelled registration");
    }
}
