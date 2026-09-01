package lk.kavindu.clinic.doctor;

import lk.kavindu.clinic.security.AppUserPrincipal;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component("doctorSecurity")
@RequiredArgsConstructor
public class DoctorSecurity {
    private final DoctorRepository doctorRepository;

    @Transactional(readOnly = true)
    public boolean isSelf(Long doctorId, Authentication authentication) {
        if (authentication == null ||
                !(authentication.getPrincipal() instanceof AppUserPrincipal principal)) {
            return false;
        }
        return doctorRepository.findWithUserById(principal.getId())
                .map(doctor -> doctor.getId().equals(doctorId))
                .orElse(false);
    }
}
