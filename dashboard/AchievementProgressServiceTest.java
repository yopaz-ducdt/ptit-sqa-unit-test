package ptit.com.enghub.service.dashboard;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.quality.Strictness;
import org.mockito.junit.jupiter.MockitoSettings;
import ptit.com.enghub.dto.request.NotificationRequest;
import ptit.com.enghub.entity.Achievement;
import ptit.com.enghub.entity.AchievementProgress;
import ptit.com.enghub.enums.NotificationType;
import ptit.com.enghub.repository.AchievementProgressRepository;
import ptit.com.enghub.service.NotificationService;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class AchievementProgressServiceTest {

    @Mock
    private AchievementProgressRepository progressRepo;

    @Mock
    private NotificationService notificationService;

    @InjectMocks
    private AchievementProgressService achievementProgressService;

    @Test
    void updateProgress_progressNotExists_shouldCreateNew() {
        Long userId = 1L;
        Achievement achievement = new Achievement();
        achievement.setId(1L);
        achievement.setConditionValue(10);
        achievement.setName("Test Achievement");

        when(progressRepo.findByUserIdAndAchievementId(userId, achievement.getId()))
                .thenReturn(Optional.empty());

        achievementProgressService.updateProgress(userId, achievement, 3);

        verify(progressRepo).save(argThat(progress -> 
            progress.getUserId().equals(userId) &&
            progress.getAchievementId().equals(achievement.getId()) &&
            progress.getCurrentValue() == 3 &&
            progress.getTargetValue() == 10
        ));
        verify(notificationService, never()).create(any(NotificationRequest.class));
    }

    @Test
    void updateProgress_beforePlusDeltaLessThanTarget_shouldUpdateNoNotification() {
        Long userId = 1L;
        Achievement achievement = new Achievement();
        achievement.setId(1L);
        achievement.setConditionValue(10);
        achievement.setName("Test");

        AchievementProgress existing = new AchievementProgress();
        existing.setUserId(userId);
        existing.setAchievementId(1L);
        existing.setCurrentValue(4);
        existing.setTargetValue(10);

        when(progressRepo.findByUserIdAndAchievementId(userId, 1L)).thenReturn(Optional.of(existing));

        achievementProgressService.updateProgress(userId, achievement, 3);

        verify(progressRepo).save(argThat(progress -> progress.getCurrentValue() == 7));
        verify(notificationService, never()).create(any());
    }

    @Test
    void updateProgress_beforePlusDeltaGreaterThanTarget_shouldCapAtTarget() {
        Long userId = 1L;
        Achievement achievement = new Achievement();
        achievement.setId(1L);
        achievement.setConditionValue(10);

        AchievementProgress existing = new AchievementProgress();
        existing.setUserId(userId);
        existing.setAchievementId(1L);
        existing.setCurrentValue(8);
        existing.setTargetValue(10);

        when(progressRepo.findByUserIdAndAchievementId(userId, 1L)).thenReturn(Optional.of(existing));

        achievementProgressService.updateProgress(userId, achievement, 5);

        verify(progressRepo).save(argThat(progress -> progress.getCurrentValue() == 10));
    }

    @Test
    void updateProgress_firstTimeReachingTarget_shouldSendNotification() {
        Long userId = 1L;
        Achievement achievement = new Achievement();
        achievement.setId(1L);
        achievement.setConditionValue(10);
        achievement.setName("Golden Achievement");

        AchievementProgress existing = new AchievementProgress();
        existing.setUserId(userId);
        existing.setAchievementId(1L);
        existing.setCurrentValue(7);
        existing.setTargetValue(10);

        when(progressRepo.findByUserIdAndAchievementId(userId, 1L)).thenReturn(Optional.of(existing));

        achievementProgressService.updateProgress(userId, achievement, 4);

        verify(notificationService).create(argThat(req -> 
            req.getType() == NotificationType.ACHIEVEMENT &&
            req.getTitle().equals("Thành tựu hoàn thành!") &&
            req.getContent().contains("Golden Achievement")
        ));
        verify(progressRepo).save(argThat(progress -> progress.getCurrentValue() == 10));
    }

    @Test
    void updateProgress_alreadyCompleted_shouldNotSendNotification() {
        Long userId = 1L;
        Achievement achievement = new Achievement();
        achievement.setId(1L);
        achievement.setConditionValue(10);

        AchievementProgress completed = new AchievementProgress();
        completed.setUserId(userId);
        completed.setAchievementId(1L);
        completed.setCurrentValue(10);
        completed.setTargetValue(10);

        when(progressRepo.findByUserIdAndAchievementId(userId, 1L)).thenReturn(Optional.of(completed));

        achievementProgressService.updateProgress(userId, achievement, 5);

        verify(progressRepo).save(argThat(progress -> progress.getCurrentValue() == 10));
        verify(notificationService, never()).create(any());
    }
}
