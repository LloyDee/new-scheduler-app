package org.volunteers.scheduler.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/** Global owner identity, independent of event membership. */
@Component
public class OwnerIdentity {
    public static final String RESERVED_PHONE = "This phone number cannot be used for a volunteer profile.";
    private final String phone;
    public OwnerIdentity(@Value("${scheduler.app-owner-phone:8135160335}") String phone) {
        this.phone = PhoneAccess.normalize(phone);
        if (this.phone.isEmpty()) throw new IllegalArgumentException("Configure a valid App Owner phone number.");
    }
    public boolean matches(String candidate) { return phone.equals(PhoneAccess.normalize(candidate)); }
    public void requireVolunteerPhone(String candidate) {
        if (matches(candidate)) throw new IllegalArgumentException(RESERVED_PHONE);
    }
}
