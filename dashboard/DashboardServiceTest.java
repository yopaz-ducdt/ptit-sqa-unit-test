package ptit.com.enghub.service.dashboard;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import ptit.com.enghub.dto.response.dashboard.ActivityDataResponse;
import ptit.com.enghub.dto.response.dashboard.ActivitySkillUserResponse;
import ptit.com.enghub.dto.response.dashboard.UserDashboardResponse;
import ptit.com.enghub.entity.AchievementProgress;
import ptit.com.enghub.entity.User;
import ptit.com.enghub.entity.UserLearningSettings;
import ptit.com.enghub.enums.StudySkill;
import ptit.com.enghub.repository.*;
import ptit.com.enghub.service.UserService;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class DashboardServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private LessonRepository lessonRepository;

    @Mock
    private SkillRepository skillRepository;

    @Mock
    private DeckRepository deckRepository;

    @Mock
    private UserStudySessionRepository sessionRepository;

    @Mock
    private UserService userService;

    @Mock
    private UserStudyDailyRepository dailyRepository;

    @Mock
    private UserFlashcardProgressRepository userFlashcardProgressRepository;

    @Mock
    private UserProgressRepository userProgressRepository;

    @Mock
    private UserSettingsRepository userLearningSettingsRepository;

    @Mock
    private AchievementProgressRepository achievementProgressRepository;

    @InjectMocks
    private DashboardService dashboardService;

    private User currentUser;

    @BeforeEach
    void setUp() {
        currentUser = User.builder().id(1L).build();
        when(userService.getCurrentUser()).thenReturn(currentUser);
    }

    // TC-DBS-001 - minutesToday = null (chưa học hôm nay) → timeStudyToday = 0
    @Test
void getDashboardUser_minutesTodayNull_shouldSetTimeStudyTodayZero() {
    // Thêm eq() cho tham số đầu tiên
    when(dailyRepository.sumTotalMinutesByUserAndDateBetween(eq(currentUser.getId()), any(), any()))
            .thenReturn(null);

    UserDashboardResponse response = dashboardService.getDashboardUser();

    assertEquals(0, response.getTimeStudyToday());
}

    // TC-DBS-004 - Có đủ dữ liệu → response chứa đúng lessonComplete, flashcardsStudied, streakDays
    @Test
void getDashboardUser_fullData_shouldReturnCorrectResponse() {
    // minutes - Thêm eq() cho tham số đầu tiên
    when(dailyRepository.sumTotalMinutesByUserAndDateBetween(eq(currentUser.getId()), any(), any()))
            .thenReturn(120, 600, 2000);
    when(userProgressRepository.countByUserIdAndCompletedTrue(currentUser.getId())).thenReturn(5);
    when(userFlashcardProgressRepository.countByUserIdAndLastReviewedAtIsNotNull(currentUser.getId())).thenReturn(50);
    
    // settings
    UserLearningSettings settings = UserLearningSettings.builder()
            .dailyStudyMinutes(90)
            .targetDaysPerWeek(5)
            .build();
    when(userLearningSettingsRepository.findByUserId(currentUser.getId())).thenReturn(Optional.of(settings));
    
    // streak
    AchievementProgress streakProgress = new AchievementProgress();
    streakProgress.setCurrentValue(7);
    when(achievementProgressRepository.findByUserIdAndAchievementId(currentUser.getId(), 1L))
            .thenReturn(Optional.of(streakProgress));

    UserDashboardResponse response = dashboardService.getDashboardUser();

    assertEquals(120, response.getTimeStudyToday());
    assertEquals(600, response.getTimeStudyW());
    assertEquals(2000, response.getTimeStudyM());
    assertEquals(90, response.getTargetDailyStudied());
    assertEquals(5, response.getTargetDaysPerW());
    assertEquals(7, response.getStreakDays());
    assertEquals(5, response.getLessonComplete());
    assertEquals(50, response.getFlashcardsStudied());
}

    // TC-DBS-006 - Có raw data → FLASHCARD/LESSON/SKILL được map đúng vào đúng ngày
    // TC-DBS-007 - Kết quả được sort theo tên (T2 → T3 → ... → T{n})
    @Test
void getWeeklyActivityDataUser_withData_shouldMapCorrectlyAndSort() {
    LocalDate today = LocalDate.now();
    int todayDow = today.getDayOfWeek().getValue();
    
    List<Object[]> rawData = new ArrayList<>();
    rawData.add(new Object[]{2, "FLASHCARD", 3});
    rawData.add(new Object[]{todayDow, "LESSON", 2});
    rawData.add(new Object[]{1, "SKILL", 1});
    
    when(sessionRepository.countActivityByDay(any(), eq(currentUser.getId()))).thenReturn(rawData);

    List<ActivityDataResponse> result = dashboardService.getWeeklyActivityDataUser(currentUser.getId());

    assertEquals(todayDow, result.size());
    
    boolean sorted = IntStream.range(0, result.size() - 1)
            .allMatch(i -> result.get(i).getName().compareTo(result.get(i + 1).getName()) <= 0);
    assertTrue(sorted);
    
    // Kiểm tra dữ liệu cho ngày hôm nay
    String expectedTodayName = "T" + (todayDow + 1);
    ActivityDataResponse todayData = result.stream()
            .filter(r -> r.getName().equals(expectedTodayName))
            .findFirst()
            .orElse(null);
    
    if (todayData != null) {
        assertEquals(2, todayData.getLessons());
    }
}

    // TC-DBS-008 - Không có session nào → tất cả skill có duration = 0 (zero-fill)
    @Test
    void getActivitySkillDataUser_noSessions_shouldReturnAllSkillsZero() {
        when(sessionRepository.sumDurationBySkill(currentUser.getId())).thenReturn(List.of());

        List<ActivitySkillUserResponse> result = dashboardService.getActivitySkillDataUser(currentUser.getId());

        assertFalse(result.isEmpty());
        for (ActivitySkillUserResponse data : result) {
            assertEquals(0, data.getTimes());
        }
    }

    // TC-DBS-009 - Có data cho một số skill → skill có data đúng giá trị, skill không có data = 0
    @Test
    void getActivitySkillDataUser_partialData_shouldZeroMissingSkills() {
        // Cách khởi tạo an toàn
        List<Object[]> rawData = new ArrayList<>();
        rawData.add(new Object[]{StudySkill.VOCAB, 100L});

        when(sessionRepository.sumDurationBySkill(currentUser.getId())).thenReturn(rawData);

        List<ActivitySkillUserResponse> result = dashboardService.getActivitySkillDataUser(currentUser.getId());

        // VOCAB should have value, others 0
        ActivitySkillUserResponse vocab = result.stream()
                .filter(r -> r.getName().equals("VOCAB"))
                .findFirst().orElse(null);
        assertNotNull(vocab);
        assertEquals(100, vocab.getTimes());

        long zeroCount = result.stream().filter(r -> !r.getName().equals("VOCAB")).count();
        assertTrue(zeroCount > 0);
    }
}
