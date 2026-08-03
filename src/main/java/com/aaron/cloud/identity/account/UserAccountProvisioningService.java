package com.aaron.cloud.identity.account;

import com.aaron.cloud.common.security.SecUserAccountRepository;
import com.aaron.cloud.common.security.UserAccountProfileSupport;
import com.aaron.cloud.common.security.entity.SecUserAccount;
import com.aaron.cloud.common.time.BeijingTime;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/** 新建 {@link SecUserAccount} 时分配 {@code account_no}、默认 {@code registered_at} 等。 */
@Service
@RequiredArgsConstructor
public class UserAccountProvisioningService {

    private final SecUserAccountRepository userAccountRepository;

    public SecUserAccount insert(SecUserAccount row) {
        if (row.getAccountNo() == null || row.getAccountNo().isBlank()) {
            row.setAccountNo(generateUniqueAccountNo());
        }
        if (row.getRegisteredAt() == null) {
            row.setRegisteredAt(BeijingTime.nowLocal());
        }
        if (row.getDisplayName() == null) {
            row.setDisplayName("");
        }
        userAccountRepository.insert(row);
        return row;
    }

    private String generateUniqueAccountNo() {
        for (int i = 0; i < 32; i++) {
            String no = UserAccountProfileSupport.generateAccountNo();
            if (!userAccountRepository.existsAccountNo(no, null)) {
                return no;
            }
        }
        throw new IllegalStateException("failed to allocate account_no");
    }
}
