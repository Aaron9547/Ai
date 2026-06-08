package com.aaron.cloud.common.knowledgeplanet;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

import com.aaron.cloud.common.api.enums.identity.UserAccountStatus;
import com.aaron.cloud.common.security.SecUserAccountRepository;
import com.aaron.cloud.common.security.SysTenantMemberRepository;
import com.aaron.cloud.common.security.entity.SecUserAccount;
import com.aaron.cloud.common.security.entity.SysTenantMember;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class KnowledgePlanetWeeklyRecipientGateTest {

    private static final long TENANT_ID = 1L;
    private static final long USER_ID = 100L;

    @Mock
    private SecUserAccountRepository userAccountRepository;

    @Mock
    private SysTenantMemberRepository tenantMemberRepository;

    @InjectMocks
    private KnowledgePlanetWeeklyRecipientGate gate;

    @Test
    void eligibleWhenAccountAndMemberActive() {
        when(userAccountRepository.findById(USER_ID)).thenReturn(Optional.of(activeUser()));
        when(tenantMemberRepository.find(TENANT_ID, USER_ID)).thenReturn(Optional.of(activeMember()));

        assertTrue(gate.isEligible(TENANT_ID, USER_ID));
        assertTrue(gate.skipReason(TENANT_ID, USER_ID).isEmpty());
    }

    @Test
    void skipWhenAccountDisabled() {
        SecUserAccount user = activeUser();
        user.setStatus(UserAccountStatus.DISABLED);
        when(userAccountRepository.findById(USER_ID)).thenReturn(Optional.of(user));

        assertFalse(gate.isEligible(TENANT_ID, USER_ID));
        assertEquals(
                KnowledgePlanetWeeklyRecipientGate.SKIP_ACCOUNT_DISABLED,
                gate.skipReason(TENANT_ID, USER_ID).orElseThrow());
    }

    @Test
    void skipWhenMemberDisabled() {
        SysTenantMember member = activeMember();
        member.setStatus(UserAccountStatus.DISABLED);
        when(userAccountRepository.findById(USER_ID)).thenReturn(Optional.of(activeUser()));
        when(tenantMemberRepository.find(TENANT_ID, USER_ID)).thenReturn(Optional.of(member));

        assertFalse(gate.isEligible(TENANT_ID, USER_ID));
        assertEquals(
                KnowledgePlanetWeeklyRecipientGate.SKIP_MEMBER_DISABLED,
                gate.skipReason(TENANT_ID, USER_ID).orElseThrow());
    }

    private static SecUserAccount activeUser() {
        SecUserAccount user = new SecUserAccount();
        user.setId(USER_ID);
        user.setStatus(UserAccountStatus.ACTIVE);
        return user;
    }

    private static SysTenantMember activeMember() {
        SysTenantMember member = new SysTenantMember();
        member.setTenantId(TENANT_ID);
        member.setUserId(USER_ID);
        member.setStatus(UserAccountStatus.ACTIVE);
        return member;
    }
}
