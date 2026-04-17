package ptit.com.enghub.service.dashboard;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import ptit.com.enghub.entity.Achievement;
import ptit.com.enghub.enums.ConditionType;
import ptit.com.enghub.repository.AchievementRepository;
import ptit.com.enghub.service.UserService;

import java.util.List;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AchievementCheckerTest {

    @Mock
    private AchievementRepository achievementRepo;

    @Mock
    private AchievementProgressService progressService;

    @InjectMocks
    private AchievementChecker achievementChecker;

    @Test
    void onFlashcardStudied_onlyTotalCardsAchievements_shouldUpdateWithCorrectDelta() {
        Long userId = 1L;
        Achievement totalCards = new Achievement();
        totalCards.setConditionType(ConditionType.TOTAL_CARDS);
        totalCards.setId(1L);
        Achievement streak = new Achievement();
        streak.setConditionType(ConditionType.STREAK_DAYS);
        streak.setId(2L);
        when(achievementRepo.findAll()).thenReturn(List.of(totalCards, streak));

        achievementChecker.onFlashcardStudied(userId, 5);

        verify(progressService).updateProgress(eq(userId), eq(totalCards), eq(5));
        verify(progressService, never()).updateProgress(eq(userId), eq(streak), anyInt());
    }

    @Test
    void onDailyStudy_onlyStreakDaysAchievements_delta1() {
        Long userId = 1L;
        Achievement streak = new Achievement();
        streak.setConditionType(ConditionType.STREAK_DAYS);
        streak.setId(1L);
        Achievement time = new Achievement();
        time.setConditionType(ConditionType.TOTAL_STUDY_TIME);
        time.setId(2L);
        when(achievementRepo.findAll()).thenReturn(List.of(streak, time));

        achievementChecker.onDailyStudy(userId);

        verify(progressService).updateProgress(eq(userId), eq(streak), eq(1));
        verify(progressService, never()).updateProgress(anyLong(), eq(time), anyInt());
    }

    @Test
    void onTotalTimeStudy_onlyTotalStudyTimeAchievements_correctDelta() {
        Long userId = 1L;
        Achievement timeAchievement = new Achievement();
        timeAchievement.setConditionType(ConditionType.TOTAL_STUDY_TIME);
        timeAchievement.setId(1L);
        Achievement cards = new Achievement();
        cards.setConditionType(ConditionType.TOTAL_CARDS);
        cards.setId(2L);
        when(achievementRepo.findAll()).thenReturn(List.of(timeAchievement, cards));

        achievementChecker.onTotalTimeStudy(userId, 30);

        verify(progressService).updateProgress(eq(userId), eq(timeAchievement), eq(30));
        verify(progressService, never()).updateProgress(eq(userId), eq(cards), anyInt());
    }
}
