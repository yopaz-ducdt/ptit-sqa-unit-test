package ptit.com.enghub.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Pageable;
import org.springframework.test.annotation.Rollback;
import org.springframework.transaction.annotation.Transactional;
import ptit.com.enghub.dto.request.StudySubmissionRequest;
import ptit.com.enghub.dto.response.FlashcardResponse;
import ptit.com.enghub.entity.Flashcard;
import ptit.com.enghub.entity.User;
import ptit.com.enghub.entity.UserFlashcardProgress;
import ptit.com.enghub.mapper.FlashcardMapper;
import ptit.com.enghub.repository.FlashcardRepository;
import ptit.com.enghub.repository.UserFlashcardProgressRepository;
import ptit.com.enghub.service.dashboard.AchievementChecker;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@Transactional
@Rollback
class StudyServiceTest {

    @Mock
    private FlashcardRepository flashcardRepository;

    @Mock
    private FlashcardMapper flashcardMapper;

    @Mock
    private UserService userService;

    @Mock
    private UserFlashcardProgressRepository progressRepository;

    @Mock
    private AchievementChecker checker;

    @InjectMocks
    private StudyService studyService;

    private User currentUser;

    @BeforeEach
    void setUp() {
        currentUser = User.builder().id(1L).build();
        when(userService.getCurrentUser()).thenReturn(currentUser);
    }

    // TC-SS-001 - Có đủ 20 due cards → trả về đúng 20 cards, không gọi findNewCards
    @Test
    void getStudySession_shouldReturnExactly20DueCardsAndNotCallFindNewCards_whenEnoughDueCards() {
        // Arrange
        Long deckId = 10L;
        List<UserFlashcardProgress> dueProgresses = new ArrayList<>();
        List<FlashcardResponse> expectedResponses = new ArrayList<>();

        for (long i = 1; i <= 20; i++) {
            Flashcard card = flashcard(i);
            dueProgresses.add(progressWithCard(card, 1, 1, 2.5, LocalDateTime.now().minusMinutes(1), null));
            FlashcardResponse response = flashcardResponse(i);
            expectedResponses.add(response);
            when(flashcardMapper.toResponse(card)).thenReturn(response);
        }

        when(progressRepository.findDueCards(eq(currentUser.getId()), eq(deckId), any(LocalDateTime.class), argThat(p -> p.getPageSize() == 20)))
                .thenReturn(dueProgresses);

        // Act
        List<FlashcardResponse> actual = studyService.getStudySession(deckId);

        // Assert
        assertNotNull(actual);
        assertEquals(20, actual.size());
        assertEquals(expectedResponses, actual);
        verify(progressRepository, times(1))
                .findDueCards(eq(currentUser.getId()), eq(deckId), any(LocalDateTime.class), argThat(p -> p.getPageSize() == 20));
        verify(progressRepository, never()).findNewCards(anyLong(), anyLong(), any(Pageable.class));
    }

    // TC-SS-002 - Không có due cards, có new cards → trả về toàn bộ new cards (tối đa 20)
    @Test
    void getStudySession_shouldReturnAllNewCards_whenNoDueCardsAndHasNewCards() {
        // Arrange
        Long deckId = 20L;
        List<UserFlashcardProgress> newProgresses = new ArrayList<>();
        List<FlashcardResponse> expectedResponses = new ArrayList<>();

        for (long i = 1; i <= 7; i++) {
            Flashcard card = flashcard(i);
            newProgresses.add(progressWithCard(card, 0, 0, 2.5, null, null));
            FlashcardResponse response = flashcardResponse(i);
            expectedResponses.add(response);
            when(flashcardMapper.toResponse(card)).thenReturn(response);
        }

        when(progressRepository.findDueCards(eq(currentUser.getId()), eq(deckId), any(LocalDateTime.class), argThat(p -> p.getPageSize() == 20)))
                .thenReturn(List.of());
        when(progressRepository.findNewCards(eq(currentUser.getId()), eq(deckId), argThat(p -> p.getPageSize() == 20)))
                .thenReturn(newProgresses);

        // Act
        List<FlashcardResponse> actual = studyService.getStudySession(deckId);

        // Assert
        assertNotNull(actual);
        assertEquals(7, actual.size());
        assertEquals(expectedResponses, actual);
        verify(progressRepository, times(1))
                .findNewCards(eq(currentUser.getId()), eq(deckId), argThat(p -> p.getPageSize() == 20));
    }

    // TC-SS-003 - Due cards < 20, new cards bù vào → tổng = due + new, không vượt 20
    @Test
    void getStudySession_shouldFillRemainingSlotsWithNewCards_whenDueCardsLessThan20() {
        // Arrange
        Long deckId = 30L;
        List<UserFlashcardProgress> dueProgresses = new ArrayList<>();
        List<UserFlashcardProgress> newProgresses = new ArrayList<>();
        List<FlashcardResponse> expectedResponses = new ArrayList<>();

        for (long i = 1; i <= 8; i++) {
            Flashcard card = flashcard(i);
            dueProgresses.add(progressWithCard(card, 1, 1, 2.5, LocalDateTime.now().minusMinutes(5), null));
            FlashcardResponse response = flashcardResponse(i);
            expectedResponses.add(response);
            when(flashcardMapper.toResponse(card)).thenReturn(response);
        }

        for (long i = 9; i <= 15; i++) {
            Flashcard card = flashcard(i);
            newProgresses.add(progressWithCard(card, 0, 0, 2.5, null, null));
            FlashcardResponse response = flashcardResponse(i);
            expectedResponses.add(response);
            when(flashcardMapper.toResponse(card)).thenReturn(response);
        }

        when(progressRepository.findDueCards(eq(currentUser.getId()), eq(deckId), any(LocalDateTime.class), argThat(p -> p.getPageSize() == 20)))
                .thenReturn(dueProgresses);
        when(progressRepository.findNewCards(eq(currentUser.getId()), eq(deckId), argThat(p -> p.getPageSize() == 12)))
                .thenReturn(newProgresses);

        // Act
        List<FlashcardResponse> actual = studyService.getStudySession(deckId);

        // Assert
        assertNotNull(actual);
        assertEquals(15, actual.size());
        assertEquals(expectedResponses, actual);
        verify(progressRepository, times(1))
                .findNewCards(eq(currentUser.getId()), eq(deckId), argThat(p -> p.getPageSize() == 12));
    }

    // TC-SS-004 - Không có due cards và không có new cards → trả về danh sách rỗng
    @Test
    void getStudySession_shouldReturnEmptyList_whenNoDueCardsAndNoNewCards() {
        // Arrange
        Long deckId = 40L;

        when(progressRepository.findDueCards(eq(currentUser.getId()), eq(deckId), any(LocalDateTime.class), argThat(p -> p.getPageSize() == 20)))
                .thenReturn(List.of());
        when(progressRepository.findNewCards(eq(currentUser.getId()), eq(deckId), argThat(p -> p.getPageSize() == 20)))
                .thenReturn(List.of());

        // Act
        List<FlashcardResponse> actual = studyService.getStudySession(deckId);

        // Assert
        assertNotNull(actual);
        assertEquals(0, actual.size());
        verify(progressRepository, times(1))
                .findNewCards(eq(currentUser.getId()), eq(deckId), argThat(p -> p.getPageSize() == 20));
        verifyNoInteractions(flashcardMapper);
    }

    // TC-SS-005 - Due cards = 19 → chỉ lấy 1 new card để bù slot còn lại
    @Test
    void getStudySession_shouldTakeOnlyOneNewCard_whenDueCardsEqual19() {
        // Arrange
        Long deckId = 50L;
        List<UserFlashcardProgress> dueProgresses = new ArrayList<>();
        List<UserFlashcardProgress> newProgresses = new ArrayList<>();
        List<FlashcardResponse> expectedResponses = new ArrayList<>();

        for (long i = 1; i <= 19; i++) {
            Flashcard card = flashcard(i);
            dueProgresses.add(progressWithCard(card, 1, 1, 2.5, LocalDateTime.now().minusMinutes(1), null));
            FlashcardResponse response = flashcardResponse(i);
            expectedResponses.add(response);
            when(flashcardMapper.toResponse(card)).thenReturn(response);
        }

        Flashcard newCard = flashcard(20L);
        newProgresses.add(progressWithCard(newCard, 0, 0, 2.5, null, null));
        FlashcardResponse newResponse = flashcardResponse(20L);
        expectedResponses.add(newResponse);
        when(flashcardMapper.toResponse(newCard)).thenReturn(newResponse);

        when(progressRepository.findDueCards(eq(currentUser.getId()), eq(deckId), any(LocalDateTime.class), argThat(p -> p.getPageSize() == 20)))
                .thenReturn(dueProgresses);
        when(progressRepository.findNewCards(eq(currentUser.getId()), eq(deckId), argThat(p -> p.getPageSize() == 1)))
                .thenReturn(newProgresses);

        // Act
        List<FlashcardResponse> actual = studyService.getStudySession(deckId);

        // Assert
        assertNotNull(actual);
        assertEquals(20, actual.size());
        assertEquals(expectedResponses, actual);
        verify(progressRepository, times(1))
                .findNewCards(eq(currentUser.getId()), eq(deckId), argThat(p -> p.getPageSize() == 1));
    }

    // TC-SS-006 - CardId không tồn tại trong DB → ném RuntimeException "Card not found"
    @Test
    void submitCardsResult_shouldThrowRuntimeException_whenCardIdNotFoundInDatabase() {
        // Arrange
        StudySubmissionRequest.CardResult result = cardResult(999L, 4);

        when(flashcardRepository.findAllByIdIn(List.of(999L))).thenReturn(List.of());
        when(progressRepository.findByUserIdAndFlashcardIdIn(currentUser.getId(), List.of(999L))).thenReturn(List.of());

        // Act
        RuntimeException exception = assertThrows(RuntimeException.class,
                () -> studyService.submitCardsResult(List.of(result)));

        // Assert
        assertEquals("Card not found: 999", exception.getMessage());
        verify(progressRepository, never()).saveAll(anyList());
        verify(checker, never()).onFlashcardStudied(anyLong(), anyInt());
    }

    // TC-SS-007 - Progress chưa tồn tại → tự tạo mới progress với giá trị mặc định rồi áp SM-2
    @Test
    void submitCardsResult_shouldCreateNewProgressWithDefaultValuesAndApplySm2_whenProgressDoesNotExist() {
        // Arrange
        Flashcard card = flashcard(101L);
        StudySubmissionRequest.CardResult result = cardResult(101L, 4);
        LocalDateTime before = LocalDateTime.now();

        when(flashcardRepository.findAllByIdIn(List.of(101L))).thenReturn(List.of(card));
        when(progressRepository.findByUserIdAndFlashcardIdIn(currentUser.getId(), List.of(101L))).thenReturn(List.of());
        when(progressRepository.saveAll(anyList())).thenAnswer(invocation -> invocation.getArgument(0));

        ArgumentCaptor<List<UserFlashcardProgress>> captor = ArgumentCaptor.forClass(List.class);

        // Act
        List<UserFlashcardProgress> actual = studyService.submitCardsResult(List.of(result));

        // Assert
        assertNotNull(actual);
        assertEquals(1, actual.size());

        verify(progressRepository, times(1)).saveAll(captor.capture());
        UserFlashcardProgress savedProgress = captor.getValue().get(0);

        assertEquals(currentUser.getId(), savedProgress.getUserId());
        assertEquals(card.getId(), savedProgress.getFlashcard().getId());
        assertEquals(1, savedProgress.getRepetitions());
        assertEquals(1, savedProgress.getIntervalDays());
        assertEquals(2.5, savedProgress.getEaseFactor(), 0.000001);
        assertNotNull(savedProgress.getLastReviewedAt());
        assertNotNull(savedProgress.getNextReviewAt());
        assertTrue(!savedProgress.getNextReviewAt().isBefore(before.plusDays(1)));
        assertTrue(!savedProgress.getLastReviewedAt().isBefore(before));
    }

    // TC-SS-008 - Quality < 3 → repetitions reset về 0, intervalDays = 1
    @Test
    void submitCardsResult_shouldResetRepetitionsAndSetIntervalToOne_whenQualityLessThanThree() {
        // Arrange
        Flashcard card = flashcard(102L);
        StudySubmissionRequest.CardResult result = cardResult(102L, 2);

        UserFlashcardProgress existingProgress = progressWithCard(
                card,
                4,
                8,
                2.3,
                LocalDateTime.now().minusDays(1),
                LocalDateTime.now().minusDays(2)
        );

        when(flashcardRepository.findAllByIdIn(List.of(102L))).thenReturn(List.of(card));
        when(progressRepository.findByUserIdAndFlashcardIdIn(currentUser.getId(), List.of(102L))).thenReturn(List.of(existingProgress));
        when(progressRepository.saveAll(anyList())).thenAnswer(invocation -> invocation.getArgument(0));

        ArgumentCaptor<List<UserFlashcardProgress>> captor = ArgumentCaptor.forClass(List.class);

        // Act
        List<UserFlashcardProgress> actual = studyService.submitCardsResult(List.of(result));

        // Assert
        assertNotNull(actual);
        verify(progressRepository).saveAll(captor.capture());
        UserFlashcardProgress savedProgress = captor.getValue().get(0);

        assertEquals(0, savedProgress.getRepetitions());
        assertEquals(1, savedProgress.getIntervalDays());
        assertEquals(2.3, savedProgress.getEaseFactor(), 0.000001);
        assertNotNull(savedProgress.getNextReviewAt());
        assertNotNull(savedProgress.getLastReviewedAt());
    }

    // TC-SS-009 - Quality >= 3, lần học đầu tiên (reps trước = 0) → repetitions = 1, intervalDays = 1
    @Test
    void submitCardsResult_shouldSetRepetitionOneAndIntervalOne_whenFirstSuccessfulStudy() {
        // Arrange
        Flashcard card = flashcard(103L);
        StudySubmissionRequest.CardResult result = cardResult(103L, 3);

        UserFlashcardProgress existingProgress = progressWithCard(
                card,
                0,
                0,
                2.5,
                null,
                null
        );

        when(flashcardRepository.findAllByIdIn(List.of(103L))).thenReturn(List.of(card));
        when(progressRepository.findByUserIdAndFlashcardIdIn(currentUser.getId(), List.of(103L))).thenReturn(List.of(existingProgress));
        when(progressRepository.saveAll(anyList())).thenAnswer(invocation -> invocation.getArgument(0));

        ArgumentCaptor<List<UserFlashcardProgress>> captor = ArgumentCaptor.forClass(List.class);

        // Act
        studyService.submitCardsResult(List.of(result));

        // Assert
        verify(progressRepository).saveAll(captor.capture());
        UserFlashcardProgress savedProgress = captor.getValue().get(0);

        assertEquals(1, savedProgress.getRepetitions());
        assertEquals(1, savedProgress.getIntervalDays());
    }

    // TC-SS-010 - Quality >= 3, lần học thứ hai (reps trước = 1) → repetitions = 2, intervalDays = 6
    @Test
    void submitCardsResult_shouldSetRepetitionTwoAndIntervalSix_whenSecondSuccessfulStudy() {
        // Arrange
        Flashcard card = flashcard(104L);
        StudySubmissionRequest.CardResult result = cardResult(104L, 4);

        UserFlashcardProgress existingProgress = progressWithCard(
                card,
                1,
                1,
                2.5,
                LocalDateTime.now().minusDays(1),
                LocalDateTime.now().minusDays(1)
        );

        when(flashcardRepository.findAllByIdIn(List.of(104L))).thenReturn(List.of(card));
        when(progressRepository.findByUserIdAndFlashcardIdIn(currentUser.getId(), List.of(104L))).thenReturn(List.of(existingProgress));
        when(progressRepository.saveAll(anyList())).thenAnswer(invocation -> invocation.getArgument(0));

        ArgumentCaptor<List<UserFlashcardProgress>> captor = ArgumentCaptor.forClass(List.class);

        // Act
        studyService.submitCardsResult(List.of(result));

        // Assert
        verify(progressRepository).saveAll(captor.capture());
        UserFlashcardProgress savedProgress = captor.getValue().get(0);

        assertEquals(2, savedProgress.getRepetitions());
        assertEquals(6, savedProgress.getIntervalDays());
    }

    // TC-SS-011 - Quality >= 3, reps >= 3 → intervalDays = ceil(intervalDays cũ × easeFactor)
    @Test
    void submitCardsResult_shouldUseCeilOfPreviousIntervalTimesEaseFactor_whenRepetitionIsAtLeastThree() {
        // Arrange
        Flashcard card = flashcard(105L);
        StudySubmissionRequest.CardResult result = cardResult(105L, 4);

        UserFlashcardProgress existingProgress = progressWithCard(
                card,
                2,
                6,
                2.5,
                LocalDateTime.now().minusDays(1),
                LocalDateTime.now().minusDays(1)
        );

        when(flashcardRepository.findAllByIdIn(List.of(105L))).thenReturn(List.of(card));
        when(progressRepository.findByUserIdAndFlashcardIdIn(currentUser.getId(), List.of(105L))).thenReturn(List.of(existingProgress));
        when(progressRepository.saveAll(anyList())).thenAnswer(invocation -> invocation.getArgument(0));

        ArgumentCaptor<List<UserFlashcardProgress>> captor = ArgumentCaptor.forClass(List.class);

        // Act
        studyService.submitCardsResult(List.of(result));

        // Assert
        verify(progressRepository).saveAll(captor.capture());
        UserFlashcardProgress savedProgress = captor.getValue().get(0);

        assertEquals(3, savedProgress.getRepetitions());
        assertEquals(15, savedProgress.getIntervalDays());
    }

    // TC-SS-012 - Quality = 0 → easeFactor giảm nhưng không xuống dưới 1.3 (floor)
    @Test
    void submitCardsResult_shouldKeepEaseFactorUnchangedForQualityZero_accordingToCurrentImplementation() {
        // Arrange
        Flashcard card = flashcard(106L);
        StudySubmissionRequest.CardResult result = cardResult(106L, 0);

        UserFlashcardProgress existingProgress = progressWithCard(
                card,
                5,
                10,
                1.3,
                LocalDateTime.now().minusDays(1),
                LocalDateTime.now().minusDays(1)
        );

        when(flashcardRepository.findAllByIdIn(List.of(106L))).thenReturn(List.of(card));
        when(progressRepository.findByUserIdAndFlashcardIdIn(currentUser.getId(), List.of(106L))).thenReturn(List.of(existingProgress));
        when(progressRepository.saveAll(anyList())).thenAnswer(invocation -> invocation.getArgument(0));

        ArgumentCaptor<List<UserFlashcardProgress>> captor = ArgumentCaptor.forClass(List.class);

        // Act
        studyService.submitCardsResult(List.of(result));

        // Assert
        verify(progressRepository).saveAll(captor.capture());
        UserFlashcardProgress savedProgress = captor.getValue().get(0);

        assertEquals(1.3, savedProgress.getEaseFactor(), 0.000001);
        assertEquals(0, savedProgress.getRepetitions());
        assertEquals(1, savedProgress.getIntervalDays());
    }

    // TC-SS-013 - Quality = 5 → easeFactor tăng đúng theo công thức SM-2
    @Test
    void submitCardsResult_shouldIncreaseEaseFactorCorrectly_whenQualityIsFive() {
        // Arrange
        Flashcard card = flashcard(107L);
        StudySubmissionRequest.CardResult result = cardResult(107L, 5);

        UserFlashcardProgress existingProgress = progressWithCard(
                card,
                2,
                6,
                2.5,
                LocalDateTime.now().minusDays(1),
                LocalDateTime.now().minusDays(1)
        );

        when(flashcardRepository.findAllByIdIn(List.of(107L))).thenReturn(List.of(card));
        when(progressRepository.findByUserIdAndFlashcardIdIn(currentUser.getId(), List.of(107L))).thenReturn(List.of(existingProgress));
        when(progressRepository.saveAll(anyList())).thenAnswer(invocation -> invocation.getArgument(0));

        ArgumentCaptor<List<UserFlashcardProgress>> captor = ArgumentCaptor.forClass(List.class);

        // Act
        studyService.submitCardsResult(List.of(result));

        // Assert
        verify(progressRepository).saveAll(captor.capture());
        UserFlashcardProgress savedProgress = captor.getValue().get(0);

        assertEquals(2.6, savedProgress.getEaseFactor(), 0.000001);
        assertEquals(3, savedProgress.getRepetitions());
        assertEquals(15, savedProgress.getIntervalDays());
    }

    // TC-SS-014 - Chỉ tính countFlashcardStudied với card có repetitions != 0 sau SM-2 → gọi checker đúng số lượng
    @Test
    void submitCardsResult_shouldCallCheckerWithOnlyCardsHavingNonZeroRepetitionsAfterSm2() {
        // Arrange
        Flashcard card1 = flashcard(108L);
        Flashcard card2 = flashcard(109L);

        StudySubmissionRequest.CardResult result1 = cardResult(108L, 5);
        StudySubmissionRequest.CardResult result2 = cardResult(109L, 1);

        UserFlashcardProgress progress1 = progressWithCard(card1, 0, 0, 2.5, null, null);
        UserFlashcardProgress progress2 = progressWithCard(card2, 3, 4, 2.5, LocalDateTime.now().minusDays(1), null);

        when(flashcardRepository.findAllByIdIn(List.of(108L, 109L))).thenReturn(List.of(card1, card2));
        when(progressRepository.findByUserIdAndFlashcardIdIn(currentUser.getId(), List.of(108L, 109L))).thenReturn(List.of(progress1, progress2));
        when(progressRepository.saveAll(anyList())).thenAnswer(invocation -> invocation.getArgument(0));

        // Act
        List<UserFlashcardProgress> actual = studyService.submitCardsResult(List.of(result1, result2));

        // Assert
        assertNotNull(actual);
        assertEquals(2, actual.size());
        verify(checker, times(1)).onFlashcardStudied(currentUser.getId(), 1);
    }

    // TC-SS-015 - Submit nhiều cards hợp lệ → saveAll được gọi 1 lần với đúng danh sách progress
    @Test
    void submitCardsResult_shouldCallSaveAllOnceWithCorrectProgressList_whenSubmittingMultipleValidCards() {
        // Arrange
        Flashcard card1 = flashcard(110L);
        Flashcard card2 = flashcard(111L);
        Flashcard card3 = flashcard(112L);

        StudySubmissionRequest.CardResult result1 = cardResult(110L, 4);
        StudySubmissionRequest.CardResult result2 = cardResult(111L, 2);
        StudySubmissionRequest.CardResult result3 = cardResult(112L, 5);

        UserFlashcardProgress progress1 = progressWithCard(card1, 0, 0, 2.5, null, null);
        UserFlashcardProgress progress2 = progressWithCard(card2, 2, 6, 2.2, LocalDateTime.now().minusDays(1), null);

        when(flashcardRepository.findAllByIdIn(List.of(110L, 111L, 112L))).thenReturn(List.of(card1, card2, card3));
        when(progressRepository.findByUserIdAndFlashcardIdIn(currentUser.getId(), List.of(110L, 111L, 112L)))
                .thenReturn(List.of(progress1, progress2));
        when(progressRepository.saveAll(anyList())).thenAnswer(invocation -> invocation.getArgument(0));

        ArgumentCaptor<List<UserFlashcardProgress>> captor = ArgumentCaptor.forClass(List.class);

        // Act
        List<UserFlashcardProgress> actual = studyService.submitCardsResult(List.of(result1, result2, result3));

        // Assert
        assertNotNull(actual);
        assertEquals(3, actual.size());

        verify(progressRepository, times(1)).saveAll(captor.capture());
        List<UserFlashcardProgress> savedProgresses = captor.getValue();

        assertEquals(3, savedProgresses.size());
        assertEquals(110L, savedProgresses.get(0).getFlashcard().getId());
        assertEquals(111L, savedProgresses.get(1).getFlashcard().getId());
        assertEquals(112L, savedProgresses.get(2).getFlashcard().getId());

        assertEquals(1, savedProgresses.get(0).getRepetitions());
        assertEquals(0, savedProgresses.get(1).getRepetitions());
        assertEquals(1, savedProgresses.get(2).getRepetitions());

        verify(progressRepository, times(1)).saveAll(anyList());
        verify(checker, times(1)).onFlashcardStudied(currentUser.getId(), 2);
    }

    private Flashcard flashcard(Long id) {
        return Flashcard.builder()
                .id(id)
                .term("term-" + id)
                .phonetic("phonetic-" + id)
                .definition("definition-" + id)
                .partOfSpeech("noun")
                .exampleSentence("example-" + id)
                .build();
    }

    private FlashcardResponse flashcardResponse(Long id) {
        FlashcardResponse response = new FlashcardResponse();
        response.setId(id);
        response.setTerm("term-" + id);
        response.setPhonetic("phonetic-" + id);
        response.setDefinition("definition-" + id);
        response.setPartOfSpeech("noun");
        response.setExampleSentence("example-" + id);
        return response;
    }

    private UserFlashcardProgress progressWithCard(
            Flashcard card,
            int repetitions,
            int intervalDays,
            double easeFactor,
            LocalDateTime nextReviewAt,
            LocalDateTime lastReviewedAt
    ) {
        return UserFlashcardProgress.builder()
                .userId(currentUser.getId())
                .flashcard(card)
                .repetitions(repetitions)
                .intervalDays(intervalDays)
                .easeFactor(easeFactor)
                .nextReviewAt(nextReviewAt)
                .lastReviewedAt(lastReviewedAt)
                .build();
    }

    private StudySubmissionRequest.CardResult cardResult(Long cardId, int quality) {
        StudySubmissionRequest.CardResult result = new StudySubmissionRequest.CardResult();
        result.setCardId(cardId);
        result.setQuality(quality);
        return result;
    }
}
