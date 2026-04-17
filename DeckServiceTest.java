package ptit.com.enghub.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import ptit.com.enghub.dto.request.DeckCreationRequest;
import ptit.com.enghub.dto.response.DeckStudyStatsResponse;
import ptit.com.enghub.dto.response.DeckSummaryResponse;
import ptit.com.enghub.entity.Deck;
import ptit.com.enghub.entity.DeckFlashcard;
import ptit.com.enghub.entity.Flashcard;
import ptit.com.enghub.entity.User;
import ptit.com.enghub.entity.UserFlashcardProgress;
import ptit.com.enghub.mapper.DeckMapper;
import ptit.com.enghub.repository.DeckFlashcardRepository;
import ptit.com.enghub.repository.DeckRepository;
import ptit.com.enghub.repository.FlashcardRepository;
import ptit.com.enghub.repository.UserFlashcardProgressRepository;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class DeckServiceTest {

    @Mock
    private DeckRepository deckRepository;

    @Mock
    private DeckMapper deckMapper;

    @Mock
    private DeckFlashcardRepository deckFlashcardRepository;

    @Mock
    private UserService userService;

    @Mock
    private UserFlashcardProgressRepository progressRepository;

    @Mock
    private FlashcardRepository flashcardRepository;

    @InjectMocks
    private DeckService deckService;

    private User currentUser;

    @BeforeEach
    void setUp() {
        currentUser = User.builder().id(99L).build();
        when(userService.getCurrentUser()).thenReturn(currentUser);
    }

    @Test
    void getDeckSummary_shouldThrowRuntimeException_whenDeckNotFound() {
        Long deckId = 1L;
        when(deckRepository.findById(deckId)).thenReturn(java.util.Optional.empty());

        RuntimeException exception = assertThrows(RuntimeException.class, () -> deckService.getDeckSummary(deckId));

        assertEquals("Deck not found", exception.getMessage());
        verify(deckRepository).findById(deckId);
        verifyNoInteractions(deckFlashcardRepository, progressRepository, deckMapper);
    }

    @Test
    void getDeckSummary_shouldReturnZeroSummary_whenDeckExistsButHasNoFlashcards() {
        Long deckId = 2L;
        Deck deck = deck(deckId, "Empty Deck", "desc", currentUser.getId(), currentUser.getId(), null, List.of());

        when(deckRepository.findById(deckId)).thenReturn(java.util.Optional.of(deck));
        when(deckFlashcardRepository.findFlashcardIdsByDeckId(deckId)).thenReturn(List.of());

        DeckSummaryResponse actual = deckService.getDeckSummary(deckId);

        assertNotNull(actual);
        assertEquals(deckId, actual.getId());
        assertEquals("Empty Deck", actual.getName());
        assertEquals(0, actual.getTotalCards());
        assertEquals(0, actual.getLearnedCards());
        assertEquals(0, actual.getDueCards());
        assertEquals(0, actual.getProgressPercent());

        verify(deckRepository).findById(deckId);
        verify(deckFlashcardRepository).findFlashcardIdsByDeckId(deckId);
        verify(progressRepository, never()).findByUserIdAndFlashcardIdIn(anyLong(), anyList());
        verifyNoInteractions(deckMapper);
    }

    @Test
    void getDeckSummary_shouldCalculateLearnedDueAndProgressPercentCorrectly() {
        Long deckId = 3L;
        Deck deck = deck(deckId, "Study Deck", "desc", currentUser.getId(), currentUser.getId(), null, List.of());
        List<Long> flashcardIds = List.of(10L, 11L, 12L, 13L, 14L);

        when(deckRepository.findById(deckId)).thenReturn(java.util.Optional.of(deck));
        when(deckFlashcardRepository.findFlashcardIdsByDeckId(deckId)).thenReturn(flashcardIds);

        LocalDateTime now = LocalDateTime.now();
        List<UserFlashcardProgress> progresses = List.of(
                progress(currentUser.getId(), flashcard(10L, "t1"), 1, 1, 2.5, now.minusMinutes(5), now.minusDays(1)),
                progress(currentUser.getId(), flashcard(11L, "t2"), 2, 6, 2.5, now.plusDays(1), now.minusDays(1)),
                progress(currentUser.getId(), flashcard(12L, "t3"), 0, 0, 2.5, null, null),
                progress(currentUser.getId(), flashcard(13L, "t4"), 0, 0, 2.5, now.minusMinutes(1), null),
                progress(currentUser.getId(), flashcard(14L, "t5"), 1, 3, 2.5, now.plusHours(2), now.minusDays(2))
        );
        when(progressRepository.findByUserIdAndFlashcardIdIn(currentUser.getId(), flashcardIds)).thenReturn(progresses);

        DeckSummaryResponse mapped = DeckSummaryResponse.builder()
                .id(deckId)
                .name("Study Deck")
                .description("desc")
                .totalCards(5)
                .sourceDeckId(null)
                .build();
        when(deckMapper.toSummaryDTO(deck)).thenReturn(mapped);

        DeckSummaryResponse actual = deckService.getDeckSummary(deckId);

        assertNotNull(actual);
        assertEquals(5, actual.getTotalCards());
        assertEquals(3, actual.getLearnedCards());
        assertEquals(2, actual.getDueCards());
        assertEquals(60, actual.getProgressPercent());

        verify(deckRepository).findById(deckId);
        verify(deckFlashcardRepository).findFlashcardIdsByDeckId(deckId);
        verify(progressRepository).findByUserIdAndFlashcardIdIn(currentUser.getId(), flashcardIds);
        verify(deckMapper).toSummaryDTO(deck);
    }

    @Test
    void createDeck_shouldCreateDeckForCurrentUserAndReturnSummary() {
        DeckCreationRequest request = DeckCreationRequest.builder()
                .name("My New Deck")
                .description("My Desc")
                .build();

        Deck savedDeck = deck(100L, "My New Deck", "My Desc", currentUser.getId(), currentUser.getId(), null, List.of());

        when(deckRepository.save(any(Deck.class))).thenReturn(savedDeck);
        when(deckRepository.findById(100L)).thenReturn(java.util.Optional.of(savedDeck));
        when(deckFlashcardRepository.findFlashcardIdsByDeckId(100L)).thenReturn(List.of());

        ArgumentCaptor<Deck> captor = ArgumentCaptor.forClass(Deck.class);

        DeckSummaryResponse actual = deckService.createDeck(request);

        assertNotNull(actual);
        assertEquals(100L, actual.getId());
        assertEquals("My New Deck", actual.getName());
        assertEquals(0, actual.getTotalCards());
        assertEquals(0, actual.getLearnedCards());
        assertEquals(0, actual.getDueCards());
        assertEquals(0, actual.getProgressPercent());

        verify(deckRepository).save(captor.capture());
        Deck deckToSave = captor.getValue();
        assertEquals("My New Deck", deckToSave.getName());
        assertEquals("My Desc", deckToSave.getDescription());
        assertEquals(currentUser.getId(), deckToSave.getOwnerId());
        assertEquals(currentUser.getId(), deckToSave.getCreatorId());
    }

    @Test
    void cloneDeck_shouldThrowRuntimeException_whenDeckNotFound() {
        Long deckId = 4L;
        when(deckRepository.findById(deckId)).thenReturn(java.util.Optional.empty());

        RuntimeException exception = assertThrows(RuntimeException.class, () -> deckService.cloneDeck(deckId));

        assertEquals("Deck not found", exception.getMessage());
        verify(deckRepository).findById(deckId);
        verify(deckRepository, never()).save(any(Deck.class));
        verifyNoInteractions(flashcardRepository, progressRepository, deckFlashcardRepository, deckMapper);
    }

    @Test
    void cloneDeck_shouldCloneDeckFlashcardsAndProgressCorrectly() {
        Long originalDeckId = 5L;

        Flashcard originalCard1 = flashcard(201L, "term-1");
        originalCard1.setPhonetic("pho-1");
        originalCard1.setDefinition("def-1");
        originalCard1.setPartOfSpeech("noun");
        originalCard1.setExampleSentence("example-1");

        Flashcard originalCard2 = flashcard(202L, "term-2");
        originalCard2.setPhonetic("pho-2");
        originalCard2.setDefinition("def-2");
        originalCard2.setPartOfSpeech("verb");
        originalCard2.setExampleSentence("example-2");

        Deck originalDeck = deck(originalDeckId, "Original", "Origin Desc", 1L, 77L, null, new ArrayList<>());
        DeckFlashcard originalDf1 = DeckFlashcard.builder().deck(originalDeck).flashcard(originalCard1).build();
        DeckFlashcard originalDf2 = DeckFlashcard.builder().deck(originalDeck).flashcard(originalCard2).build();
        originalDeck.setDeckFlashcards(List.of(originalDf1, originalDf2));

        when(deckRepository.findById(originalDeckId)).thenReturn(java.util.Optional.of(originalDeck));

        Deck firstSavedDeck = deck(300L, "Original", "Origin Desc", currentUser.getId(), 77L, originalDeckId, new ArrayList<>());
        Deck secondSavedDeck = deck(300L, "Original", "Origin Desc", currentUser.getId(), 77L, originalDeckId, new ArrayList<>());
        when(deckRepository.save(any(Deck.class))).thenReturn(firstSavedDeck, secondSavedDeck);

        final long[] flashcardIdSequence = {400L};
        when(flashcardRepository.save(any(Flashcard.class))).thenAnswer(invocation -> {
            Flashcard arg = invocation.getArgument(0);
            arg.setId(flashcardIdSequence[0]++);
            return arg;
        });

        when(progressRepository.save(any(UserFlashcardProgress.class))).thenAnswer(invocation -> invocation.getArgument(0));

        when(deckFlashcardRepository.findFlashcardIdsByDeckId(300L)).thenReturn(List.of(400L, 401L));
        when(progressRepository.findByUserIdAndFlashcardIdIn(currentUser.getId(), List.of(400L, 401L))).thenReturn(List.of(
                progress(currentUser.getId(), flashcard(400L, "term-1"), 0, 0, 2.5, null, null),
                progress(currentUser.getId(), flashcard(401L, "term-2"), 0, 0, 2.5, null, null)
        ));

        Deck cloneDeckForSummary = deck(300L, "Original", "Origin Desc", currentUser.getId(), 77L, originalDeckId, new ArrayList<>());
        when(deckRepository.findById(300L)).thenReturn(java.util.Optional.of(cloneDeckForSummary));

        DeckSummaryResponse mappedSummary = DeckSummaryResponse.builder()
                .id(300L)
                .name("Original")
                .description("Origin Desc")
                .totalCards(2)
                .sourceDeckId(originalDeckId)
                .build();
        when(deckMapper.toSummaryDTO(cloneDeckForSummary)).thenReturn(mappedSummary);

        ArgumentCaptor<Deck> deckCaptor = ArgumentCaptor.forClass(Deck.class);
        ArgumentCaptor<Flashcard> flashcardCaptor = ArgumentCaptor.forClass(Flashcard.class);
        ArgumentCaptor<UserFlashcardProgress> progressCaptor = ArgumentCaptor.forClass(UserFlashcardProgress.class);

        DeckSummaryResponse actual = deckService.cloneDeck(originalDeckId);

        assertNotNull(actual);
        assertEquals(300L, actual.getId());
        assertEquals(2, actual.getTotalCards());
        assertEquals(0, actual.getLearnedCards());
        assertEquals(0, actual.getDueCards());
        assertEquals(0, actual.getProgressPercent());

        verify(deckRepository, times(2)).save(deckCaptor.capture());
        List<Deck> savedDecks = deckCaptor.getAllValues();

        Deck clonedDeckFirstSave = savedDecks.get(0);
        assertEquals(currentUser.getId(), clonedDeckFirstSave.getOwnerId());
        assertEquals(originalDeck.getCreatorId(), clonedDeckFirstSave.getCreatorId());
        assertEquals(originalDeck.getId(), clonedDeckFirstSave.getSourceDeckId());

        Deck clonedDeckSecondSave = savedDecks.get(1);
        assertNotNull(clonedDeckSecondSave.getDeckFlashcards());
        assertEquals(2, clonedDeckSecondSave.getDeckFlashcards().size());

        verify(flashcardRepository, times(2)).save(flashcardCaptor.capture());
        List<Flashcard> savedFlashcards = flashcardCaptor.getAllValues();

        assertEquals(2, savedFlashcards.size());
        assertNotSame(originalCard1, savedFlashcards.get(0));
        assertNotSame(originalCard2, savedFlashcards.get(1));
        assertEquals(originalCard1.getTerm(), savedFlashcards.get(0).getTerm());
        assertEquals(originalCard2.getTerm(), savedFlashcards.get(1).getTerm());

        verify(progressRepository, times(2)).save(progressCaptor.capture());
        List<UserFlashcardProgress> savedProgresses = progressCaptor.getAllValues();
        assertEquals(2, savedProgresses.size());

        for (UserFlashcardProgress progress : savedProgresses) {
            assertEquals(currentUser.getId(), progress.getUserId());
            assertEquals(2.5, progress.getEaseFactor(), 0.000001);
            assertEquals(0, progress.getRepetitions());
            assertEquals(0, progress.getIntervalDays());
            assertNull(progress.getNextReviewAt());
        }

        verify(deckRepository).findById(originalDeckId);
        verify(deckRepository).findById(300L);
        verify(deckFlashcardRepository).findFlashcardIdsByDeckId(300L);
        verify(progressRepository).findByUserIdAndFlashcardIdIn(currentUser.getId(), List.of(400L, 401L));
        verify(deckMapper).toSummaryDTO(cloneDeckForSummary);
    }

    @Test
    void deleteDeck_shouldThrowRuntimeException_whenDeckNotFound() {
        Long deckId = 6L;
        when(deckRepository.existsById(deckId)).thenReturn(false);

        RuntimeException exception = assertThrows(RuntimeException.class, () -> deckService.deleteDeck(deckId));

        assertEquals("Deck not found", exception.getMessage());
        verify(deckRepository).existsById(deckId);
        verify(deckFlashcardRepository, never()).findFlashcardIdsByDeckId(anyLong());
        verify(progressRepository, never()).deleteByUserIdAndFlashcardIdIn(anyLong(), anyList());
        verify(deckFlashcardRepository, never()).deleteByDeckId(anyLong());
        verify(deckRepository, never()).deleteById(anyLong());
    }

    @Test
    void deleteDeck_shouldDeleteProgressDeckFlashcardAndDeck_whenDeckHasFlashcards() {
        Long deckId = 7L;
        List<Long> flashcardIds = List.of(501L, 502L);

        when(deckRepository.existsById(deckId)).thenReturn(true);
        when(deckFlashcardRepository.findFlashcardIdsByDeckId(deckId)).thenReturn(flashcardIds);

        deckService.deleteDeck(deckId);

        verify(deckRepository).existsById(deckId);
        verify(deckFlashcardRepository).findFlashcardIdsByDeckId(deckId);
        verify(progressRepository).deleteByUserIdAndFlashcardIdIn(currentUser.getId(), flashcardIds);
        verify(deckFlashcardRepository).deleteByDeckId(deckId);
        verify(deckRepository).deleteById(deckId);
    }

    @Test
    void deleteDeck_shouldSkipDeletingProgress_whenDeckHasNoFlashcards() {
        Long deckId = 8L;

        when(deckRepository.existsById(deckId)).thenReturn(true);
        when(deckFlashcardRepository.findFlashcardIdsByDeckId(deckId)).thenReturn(List.of());

        deckService.deleteDeck(deckId);

        verify(deckRepository).existsById(deckId);
        verify(deckFlashcardRepository).findFlashcardIdsByDeckId(deckId);
        verify(progressRepository, never()).deleteByUserIdAndFlashcardIdIn(anyLong(), anyList());
        verify(deckFlashcardRepository).deleteByDeckId(deckId);
        verify(deckRepository).deleteById(deckId);
    }

    @Test
    void resetDeckProgress_shouldReturnEarly_whenDeckHasNoFlashcards() {
        Long deckId = 9L;
        when(deckFlashcardRepository.findFlashcardIdsByDeckId(deckId)).thenReturn(List.of());

        deckService.resetDeckProgress(deckId);

        verify(deckFlashcardRepository).findFlashcardIdsByDeckId(deckId);
        verify(progressRepository, never()).resetProgress(anyLong(), anyList());
    }

    @Test
    void resetDeckProgress_shouldResetProgress_whenDeckHasFlashcards() {
        Long deckId = 10L;
        List<Long> flashcardIds = List.of(601L, 602L);
        when(deckFlashcardRepository.findFlashcardIdsByDeckId(deckId)).thenReturn(flashcardIds);

        deckService.resetDeckProgress(deckId);

        verify(deckFlashcardRepository).findFlashcardIdsByDeckId(deckId);
        verify(progressRepository).resetProgress(currentUser.getId(), flashcardIds);
    }

    @Test
    void getDeckStats_shouldThrowRuntimeException_whenDeckNotFound() {
        Long deckId = 11L;
        when(deckRepository.findById(deckId)).thenReturn(java.util.Optional.empty());

        RuntimeException exception = assertThrows(RuntimeException.class, () -> deckService.getDeckStats(deckId));

        assertEquals("Deck not found", exception.getMessage());
        verify(deckRepository).findById(deckId);
        verifyNoInteractions(flashcardRepository, progressRepository);
    }

    @Test
    void getDeckStats_shouldReturnZeroTotalCards_whenNoFlashcards() {
        Long deckId = 12L;
        Deck deck = deck(deckId, "Stats Deck", "desc", currentUser.getId(), currentUser.getId(), null, List.of());

        when(deckRepository.findById(deckId)).thenReturn(java.util.Optional.of(deck));
        when(flashcardRepository.findByDeckId(deckId)).thenReturn(List.of());

        DeckStudyStatsResponse actual = deckService.getDeckStats(deckId);

        assertNotNull(actual);
        assertEquals(deckId, actual.getDeckId());
        assertEquals("Stats Deck", actual.getDeckName());
        assertEquals(0, actual.getTotalCards());

        verify(deckRepository).findById(deckId);
        verify(flashcardRepository).findByDeckId(deckId);
        verify(progressRepository, never()).findByUserIdAndFlashcardIdIn(anyLong(), anyList());
    }

    @Test
    void getDeckStats_shouldCalculateAllFieldsCorrectly_whenFlashcardsExist() {
        Long deckId = 13L;
        Deck deck = deck(deckId, "Deck Stats", "desc", currentUser.getId(), currentUser.getId(), null, List.of());

        Flashcard card1 = flashcard(701L, "c1");
        Flashcard card2 = flashcard(702L, "c2");
        Flashcard card3 = flashcard(703L, "c3");
        Flashcard card4 = flashcard(704L, "c4");

        List<Flashcard> flashcards = List.of(card1, card2, card3, card4);

        LocalDateTime now = LocalDateTime.now();
        LocalDateTime todayMorning = LocalDate.now().atStartOfDay().plusHours(9);
        LocalDateTime yesterday = LocalDate.now().minusDays(1).atStartOfDay().plusHours(10);

        List<UserFlashcardProgress> progresses = List.of(
                progress(currentUser.getId(), card1, 0, 0, 2.5, now.minusMinutes(5), todayMorning),
                progress(currentUser.getId(), card2, 2, 6, 2.5, now.plusDays(1), yesterday),
                progress(currentUser.getId(), card3, 1, 3, 2.5, now, now.plusHours(1)),
                progress(currentUser.getId(), card4, 0, 0, 2.5, null, null)
        );

        when(deckRepository.findById(deckId)).thenReturn(java.util.Optional.of(deck));
        when(flashcardRepository.findByDeckId(deckId)).thenReturn(flashcards);
        when(progressRepository.findByUserIdAndFlashcardIdIn(currentUser.getId(), List.of(701L, 702L, 703L, 704L)))
                .thenReturn(progresses);

        DeckStudyStatsResponse actual = deckService.getDeckStats(deckId);

        assertNotNull(actual);
        assertEquals(deckId, actual.getDeckId());
        assertEquals("Deck Stats", actual.getDeckName());
        assertEquals(4, actual.getTotalCards());
        assertEquals(2, actual.getLearningCards());
        assertEquals(2, actual.getReviewCards());
        assertEquals(2, actual.getDueTodayCards());
        assertEquals(2, actual.getStudiedToday());
        assertEquals(3, actual.getTotalReviews());
        assertEquals(50.0, actual.getProgressPercent(), 0.000001);
        assertEquals(now.plusHours(1).withNano(0), actual.getLastStudyAt().withNano(0));

        verify(deckRepository).findById(deckId);
        verify(flashcardRepository).findByDeckId(deckId);
        verify(progressRepository).findByUserIdAndFlashcardIdIn(currentUser.getId(), List.of(701L, 702L, 703L, 704L));
    }

    private Deck deck(Long id, String name, String description, Long ownerId, Long creatorId, Long sourceDeckId, List<DeckFlashcard> deckFlashcards) {
        return Deck.builder()
                .id(id)
                .name(name)
                .description(description)
                .ownerId(ownerId)
                .creatorId(creatorId)
                .sourceDeckId(sourceDeckId)
                .deckFlashcards(deckFlashcards)
                .build();
    }

    private Flashcard flashcard(Long id, String term) {
        return Flashcard.builder()
                .id(id)
                .term(term)
                .build();
    }

    private UserFlashcardProgress progress(Long userId,
                                           Flashcard flashcard,
                                           int repetitions,
                                           int intervalDays,
                                           double easeFactor,
                                           LocalDateTime nextReviewAt,
                                           LocalDateTime lastReviewedAt) {
        return UserFlashcardProgress.builder()
                .userId(userId)
                .flashcard(flashcard)
                .repetitions(repetitions)
                .intervalDays(intervalDays)
                .easeFactor(easeFactor)
                .nextReviewAt(nextReviewAt)
                .lastReviewedAt(lastReviewedAt)
                .build();
    }
}
