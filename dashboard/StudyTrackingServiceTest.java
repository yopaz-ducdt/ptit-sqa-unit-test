package ptit.com.enghub.service.dashboard;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.quality.Strictness;
import org.mockito.junit.jupiter.MockitoSettings;
import ptit.com.enghub.dto.EndStudyDto;
import ptit.com.enghub.dto.request.StartStudyRequest;
import ptit.com.enghub.dto.response.StudyChartResponse;
import ptit.com.enghub.entity.User;
import ptit.com.enghub.entity.UserStudyDaily;
import ptit.com.enghub.entity.UserStudySession;
import ptit.com.enghub.exception.AppException;
import ptit.com.enghub.enums.StudyActivityType;
import ptit.com.enghub.enums.StudySkill;
import ptit.com.enghub.exception.ErrorCode;
import ptit.com.enghub.repository.UserStudyDailyRepository;
import ptit.com.enghub.repository.UserStudySessionRepository;
import ptit.com.enghub.service.UserService;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class StudyTrackingServiceTest {

    @Mock
    private UserService userService;

    @Mock
    private UserStudySessionRepository sessionRepo;

    @Mock
    private UserStudyDailyRepository dailyRepo;

    @Mock
    private AchievementChecker checker;

    @InjectMocks
    private StudyTrackingService studyTrackingService;

    private User currentUser;

    @BeforeEach
    void setUp() {
        currentUser = User.builder()
                .id(1L)
                .build();
        when(userService.getCurrentUser()).thenReturn(currentUser);
    }

    // TC-STS-001 - Đây là session đầu tiên trong ngày → gọi checker.onDailyStudy một lần
    // TC-STS-003 - Tạo session thành công → session có đủ activityType, skill, lessonId, deckId từ request
    @Test
    void startStudy_firstSessionToday_shouldCallCheckerOnce() {
        StartStudyRequest request = StartStudyRequest.builder()
                .activityType(StudyActivityType.LESSON)
                .skill(StudySkill.VOCAB)
                .lessonId(null)
                .deckId(123L)
                .build();

        doReturn(false).when(sessionRepo).existsByUserIdAndStartedAtBetween(eq(currentUser.getId()), any(), any());

        EndStudyDto response = studyTrackingService.startStudy(request);

        verify(checker).onDailyStudy(currentUser.getId());
        ArgumentCaptor<UserStudySession> sessionCaptor = ArgumentCaptor.forClass(UserStudySession.class);
        verify(sessionRepo).save(sessionCaptor.capture());
        UserStudySession savedSession = sessionCaptor.getValue();
        assertEquals(currentUser.getId(), savedSession.getUserId());
        assertEquals(StudyActivityType.LESSON, savedSession.getActivityType());
        assertEquals(StudySkill.VOCAB, savedSession.getSkill());
        assertEquals(123L, savedSession.getDeckId());
        assertNotNull(savedSession.getStartedAt());
        assertNull(savedSession.getEndedAt());
    }

    // TC-STS-002 - Đã có session trong ngày → không gọi checker.onDailyStudy
    @Test
    void startStudy_alreadyHasSessionToday_shouldNotCallChecker() {
        StartStudyRequest request = StartStudyRequest.builder().build();

        doReturn(true).when(sessionRepo).existsByUserIdAndStartedAtBetween(eq(currentUser.getId()), any(), any());

        studyTrackingService.startStudy(request);

        verify(checker, never()).onDailyStudy(anyLong());
        verify(sessionRepo).save(any(UserStudySession.class));
    }

    // TC-STS-004 - SessionId không tồn tại → ném RuntimeException "Session not found"
    @Test
    void endStudy_sessionIdNotFound_shouldThrowRuntimeException() {
        EndStudyDto request = new EndStudyDto();
        request.setSessionId(999L);

        when(sessionRepo.findById(999L)).thenReturn(Optional.empty());

        RuntimeException exception = assertThrows(RuntimeException.class, 
            () -> studyTrackingService.endStudy(request));

        assertEquals("Session not found", exception.getMessage());
        verify(sessionRepo).findById(999L);
    }

    // TC-STS-005 - SessionId = null, không có session nào của user → ném RuntimeException "No active session"
    @Test
    void endStudy_sessionIdNullNoActiveSession_shouldThrowRuntimeException() {
        EndStudyDto request = new EndStudyDto();
        request.setSessionId(null);

        when(sessionRepo.findTopByUserIdOrderByStartedAtDesc(currentUser.getId()))
                .thenReturn(Optional.empty());

        RuntimeException exception = assertThrows(RuntimeException.class, 
            () -> studyTrackingService.endStudy(request));

        assertEquals("No active session", exception.getMessage());
    }

    // TC-STS-006 - Session thuộc user khác → ném AppException UNAUTHORIZED
    @Test
    void endStudy_sessionBelongsToAnotherUser_shouldThrowAppException() {
        EndStudyDto request = new EndStudyDto();
        request.setSessionId(100L);

        UserStudySession otherSession = UserStudySession.builder()
                .id(100L)
                .userId(2L)
                .build();

        when(sessionRepo.findById(100L)).thenReturn(Optional.of(otherSession));

        AppException exception = assertThrows(AppException.class, 
            () -> studyTrackingService.endStudy(request));

        assertEquals(ErrorCode.UNAUTHORIZED, exception.getErrorCode());
    }

    // TC-STS-007 - Session đã có endedAt → return sớm, không làm gì thêm
    @Test
    void endStudy_sessionAlreadyEnded_shouldReturnEarly() {
        EndStudyDto request = new EndStudyDto();
        request.setSessionId(200L);

        UserStudySession endedSession = UserStudySession.builder()
                .id(200L)
                .userId(currentUser.getId())
                .endedAt(LocalDateTime.now())
                .build();

        when(sessionRepo.findById(200L)).thenReturn(Optional.of(endedSession));

        studyTrackingService.endStudy(request);

        verify(sessionRepo, never()).save(any());
        verify(dailyRepo, never()).save(any());
        verify(checker, never()).onTotalTimeStudy(anyLong(), anyInt());
    }

    // TC-STS-008 - Duration < 1 phút → durationMinutes được set = 1 (floor)
    // TC-STS-011 - Kết thúc thành công → gọi checker.onTotalTimeStudy với đúng durationMinutes
    @Test
    void endStudy_durationLessThanMinute_shouldSetDurationTo1() {
        EndStudyDto request = new EndStudyDto();
        request.setSessionId(300L);

        UserStudySession shortSession = UserStudySession.builder()
                .id(300L)
                .userId(currentUser.getId())
                .startedAt(LocalDateTime.now().minusSeconds(30))
                .endedAt(null)
                .build();

        when(sessionRepo.findById(300L)).thenReturn(Optional.of(shortSession));

        when(dailyRepo.findByUserIdAndStudyDate(eq(currentUser.getId()), any()))
                .thenReturn(Optional.empty());

        studyTrackingService.endStudy(request);

        verify(sessionRepo).save(argThat(session -> 
            session.getDurationMinutes() == 1
        ));
        verify(checker).onTotalTimeStudy(currentUser.getId(), 1);
    }

    // TC-STS-009 - Kết thúc thành công, chưa có UserStudyDaily → tạo mới daily với đúng totalMinutes, sessionCount=1
    // TC-STS-011 - Kết thúc thành công → gọi checker.onTotalTimeStudy với đúng durationMinutes
    @Test
    void endStudy_noUserStudyDaily_shouldCreateNew() {
        EndStudyDto request = new EndStudyDto();
        request.setSessionId(400L);

        LocalDateTime startedAt = LocalDateTime.now().minusMinutes(5);
        UserStudySession session = UserStudySession.builder()
                .id(400L)
                .userId(currentUser.getId())
                .startedAt(startedAt)
                .endedAt(null)
                .build();

        when(sessionRepo.findById(400L)).thenReturn(Optional.of(session));
        doReturn(Optional.empty()).when(dailyRepo).findByUserIdAndStudyDate(eq(currentUser.getId()), eq(startedAt.toLocalDate()));

        studyTrackingService.endStudy(request);

        verify(dailyRepo).save(argThat(daily -> 
            daily.getTotalMinutes() == 5 &&
            daily.getSessionCount() == 1 &&
            daily.getFirstStudyAt() != null &&
            daily.getUserId().equals(currentUser.getId())
        ));
    }

    // TC-STS-010 - Kết thúc thành công, đã có UserStudyDaily → cộng dồn totalMinutes, sessionCount tăng 1
    // TC-STS-011 - Kết thúc thành công → gọi checker.onTotalTimeStudy với đúng durationMinutes
    @Test
    void endStudy_existingUserStudyDaily_shouldAccumulate() {
        EndStudyDto request = new EndStudyDto();
        request.setSessionId(500L);

        LocalDateTime startedAt = LocalDateTime.now().minusMinutes(10);
        LocalDate studyDate = startedAt.toLocalDate();
        UserStudyDaily existingDaily = UserStudyDaily.builder()
                .userId(currentUser.getId())
                .studyDate(studyDate)
                .totalMinutes(15)
                .sessionCount(2)
                .build();

        UserStudySession session = UserStudySession.builder()
                .id(500L)
                .userId(currentUser.getId())
                .startedAt(startedAt)
                .endedAt(null)
                .build();

        when(sessionRepo.findById(500L)).thenReturn(Optional.of(session));
        when(dailyRepo.findByUserIdAndStudyDate(currentUser.getId(), studyDate))
                .thenReturn(Optional.of(existingDaily));

        studyTrackingService.endStudy(request);

        verify(dailyRepo).save(argThat(daily -> 
            daily.getTotalMinutes() == 25 &&
            daily.getSessionCount() == 3
        ));
        verify(checker).onTotalTimeStudy(currentUser.getId(), 10);
    }

    // TC-STS-012 - Trả về đúng 28 điểm dữ liệu (4 tuần × 7 ngày)
    // TC-STS-013 - Ngày không có trong DB → minutes = 0 (zero-fill)
    @Test
    void getLast4WeeksWeekdayChart_shouldReturnExactly28Entries() {
        List<Object[]> rawData = List.of(
            new Object[]{LocalDate.now().minusDays(1), 60},
            new Object[]{LocalDate.now().minusDays(5), 30}
        );

        when(dailyRepo.findDailyStudyMinutes(eq(currentUser.getId()), any()))
                .thenReturn(rawData);

        List<StudyChartResponse> result = studyTrackingService.getLast4WeeksWeekdayChart();

        assertEquals(28, result.size());
        long zeroCount = result.stream().map(StudyChartResponse::getMinutes).filter(m -> m == 0).count();
        assertTrue(zeroCount > 0);
    }

    // TC-STS-014 - Có session trong ngày hôm nay → trả về true
    @Test
    void hasSessionToday_hasSession_shouldReturnTrue() {
        doReturn(true).when(sessionRepo).existsByUserIdAndStartedAtBetween(eq(currentUser.getId()), any(), any());

        boolean result = studyTrackingService.hasSessionToday(currentUser.getId());

        assertTrue(result);
    }

    // TC-STS-015 - Không có session nào hôm nay → trả về false
    @Test
    void hasSessionToday_noSession_shouldReturnFalse() {
        doReturn(false).when(sessionRepo).existsByUserIdAndStartedAtBetween(eq(currentUser.getId()), any(), any());

        boolean result = studyTrackingService.hasSessionToday(currentUser.getId());

        assertFalse(result);
    }
}

