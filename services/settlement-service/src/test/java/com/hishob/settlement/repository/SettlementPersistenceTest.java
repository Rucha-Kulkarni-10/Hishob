package com.hishob.settlement.repository;

import com.hishob.settlement.entity.Settlement;
import com.hishob.settlement.entity.SettlementStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Transactional
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class SettlementPersistenceTest {

    @Autowired
    private SettlementRepository settlementRepository;

    @Test
    @DisplayName("Persist and retrieve group settlement with non-null groupId")
    void testPersistAndRetrieve_GroupSettlement_WithGroupId() {
        UUID groupId = UUID.randomUUID();
        UUID fromUser = UUID.randomUUID();
        UUID toUser = UUID.randomUUID();

        Settlement settlement = new Settlement(groupId, fromUser, toUser, new BigDecimal("150.00"), SettlementStatus.COMPLETED);
        settlement.setNotes("Group dinner settlement");

        Settlement saved = settlementRepository.saveAndFlush(settlement);
        assertThat(saved.getId()).isNotNull();
        assertThat(saved.getGroupId()).isEqualTo(groupId);

        Optional<Settlement> retrieved = settlementRepository.findById(saved.getId());
        assertThat(retrieved).isPresent();
        assertThat(retrieved.get().getGroupId()).isEqualTo(groupId);
        assertThat(retrieved.get().getFromUserId()).isEqualTo(fromUser);
        assertThat(retrieved.get().getToUserId()).isEqualTo(toUser);
        assertThat(retrieved.get().getAmount()).isEqualByComparingTo("150.00");
        assertThat(retrieved.get().getNotes()).isEqualTo("Group dinner settlement");

        List<Settlement> groupSettlements = settlementRepository.findByGroupIdOrderByCreatedAtDesc(groupId);
        assertThat(groupSettlements).hasSize(1);
        assertThat(groupSettlements.get(0).getId()).isEqualTo(saved.getId());
    }

    @Test
    @DisplayName("Persist and retrieve personal settlement with null groupId")
    void testPersistAndRetrieve_PersonalSettlement_WithNullGroupId() {
        UUID fromUser = UUID.randomUUID();
        UUID toUser = UUID.randomUUID();

        Settlement settlement = new Settlement(null, fromUser, toUser, new BigDecimal("333.33"), SettlementStatus.COMPLETED);
        settlement.setNotes("Direct friend settlement");

        Settlement saved = settlementRepository.saveAndFlush(settlement);
        assertThat(saved.getId()).isNotNull();
        assertThat(saved.getGroupId()).isNull();

        Optional<Settlement> retrieved = settlementRepository.findById(saved.getId());
        assertThat(retrieved).isPresent();
        assertThat(retrieved.get().getGroupId()).isNull();
        assertThat(retrieved.get().getFromUserId()).isEqualTo(fromUser);
        assertThat(retrieved.get().getToUserId()).isEqualTo(toUser);
        assertThat(retrieved.get().getAmount()).isEqualByComparingTo("333.33");
        assertThat(retrieved.get().getNotes()).isEqualTo("Direct friend settlement");

        // Personal history queries find it
        List<Settlement> personalHistory = settlementRepository.findPersonalHistoryForUser(fromUser);
        assertThat(personalHistory).anyMatch(s -> s.getId().equals(saved.getId()) && s.getGroupId() == null);

        List<Settlement> betweenUsers = settlementRepository.findPersonalHistoryBetweenUsers(fromUser, toUser);
        assertThat(betweenUsers).anyMatch(s -> s.getId().equals(saved.getId()) && s.getGroupId() == null);

        List<Settlement> completedPersonal = settlementRepository.findPersonalByUserIdAndStatus(fromUser, SettlementStatus.COMPLETED);
        assertThat(completedPersonal).anyMatch(s -> s.getId().equals(saved.getId()) && s.getGroupId() == null);

        // Group settlement query must NOT include personal settlements
        List<Settlement> randomGroup = settlementRepository.findByGroupIdOrderByCreatedAtDesc(UUID.randomUUID());
        assertThat(randomGroup).noneMatch(s -> s.getId().equals(saved.getId()));
    }

}
