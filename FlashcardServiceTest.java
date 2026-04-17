package ptit.com.enghub.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import ptit.com.enghub.dto.request.AddFlashcardRequest;
import ptit.com.enghub.dto.request.BulkFlashcardRequest;
import ptit.com.enghub.dto.request.FlashcardRequest;
import ptit.com.enghub.dto.response.BulkFlashcardResponse;
import ptit.com.enghub.dto.response.FlashcardResponse;
import ptit.com.enghub.entity.*;
import ptit.com.enghub.mapper.FlashcardMapper;
import ptit.com.enghub.repository.DeckRepository;
import ptit.com.enghub.repository.FlashcardRepository;
import ptit.com.enghub.repository.UserFlashcardProgressRepository;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@MockitoSettings(strictness = Strictness.LENIENT)
@ExtendWith(MockitoExtension.class)
class FlashcardServiceTest {

    @Mock
    private FlashcardRepository flashcardRepository;
    @Mock
    private DeckRepository deckRepository;
    @Mock
    private FlashcardMapper flashcardMapper;
    @Mock
    private UserService userService;
    @Mock
    private UserFlashcardProgressRepository progressRepository;
    @Mock
    private AIService aiService;

    @InjectMocks
    private FlashcardService flashcardService;

    private User currentUser;

    @BeforeEach
    void setUp() {
        currentUser = User.builder().id(100L).build();
        when(userService.getCurrentUser()).thenReturn(currentUser);
    }

    @Test
    void createFlashcard_shouldThrowRuntimeException_whenDeckIdProvidedButDeckNotFound() {
        FlashcardRequest request = FlashcardRequest.builder()
                .deckId(1L)
                .term("hello")
                .build();
        Flashcard flashcard = new Flashcard();

        when(flashcardMapper.toEntity(request)).thenReturn(flashcard);
        when(deckRepository.findById(1L)).thenReturn(Optional.empty());

        RuntimeException exception = assertThrows(RuntimeException.class, () -> flashcardService.createFlashcard(request));

        assertEquals("Deck not found with id: 1", exception.getMessage());
        verify(flashcardMapper).toEntity(request);
        verify(deckRepository).findById(1L);
        verify(flashcardRepository, never()).save(any());
        verify(progressRepository, never()).save(any());
    }

    @Test
    void createFlashcard_shouldCreateDeckFlashcardAndInitializeProgress_whenDeckExists() {
        FlashcardRequest request = FlashcardRequest.builder()
                .deckId(2L)
                .term("apple")
                .phonetic("a")
                .definition("fruit")
                .partOfSpeech("noun")
                .exampleSentence("An apple")
                .build();

        Deck deck = Deck.builder().id(2L).ownerId(200L).build();
        Flashcard mappedFlashcard = new Flashcard();
        Flashcard savedFlashcard = Flashcard.builder().id(10L).term("apple").build();
        FlashcardResponse response = new FlashcardResponse();
        response.setId(10L);

        when(flashcardMapper.toEntity(request)).thenReturn(mappedFlashcard);
        when(deckRepository.findById(2L)).thenReturn(Optional.of(deck));
        when(flashcardRepository.save(mappedFlashcard)).thenReturn(savedFlashcard);
        when(flashcardMapper.toResponse(savedFlashcard)).thenReturn(response);

        ArgumentCaptor<UserFlashcardProgress> progressCaptor = ArgumentCaptor.forClass(UserFlashcardProgress.class);

        FlashcardResponse actual = flashcardService.createFlashcard(request);

        assertNotNull(actual);
        assertEquals(10L, actual.getId());
        assertNotNull(mappedFlashcard.getDeckFlashcards());
        assertEquals(1, mappedFlashcard.getDeckFlashcards().size());

        DeckFlashcard deckFlashcard = mappedFlashcard.getDeckFlashcards().get(0);
        assertSame(deck, deckFlashcard.getDeck());
        assertSame(mappedFlashcard, deckFlashcard.getFlashcard());

        verify(deckRepository).findById(2L);
        verify(flashcardRepository).save(mappedFlashcard);
        verify(progressRepository).save(progressCaptor.capture());

        UserFlashcardProgress savedProgress = progressCaptor.getValue();
        assertEquals(currentUser.getId(), savedProgress.getUserId());
        assertSame(savedFlashcard, savedProgress.getFlashcard());
        assertEquals(2.5, savedProgress.getEaseFactor(), 0.000001);
        assertEquals(0, savedProgress.getRepetitions());
        assertEquals(0, savedProgress.getIntervalDays());
        assertNull(savedProgress.getNextReviewAt());
    }

    @Test
    void createFlashcard_shouldNotCreateDeckFlashcard_whenDeckIdIsNull() {
        FlashcardRequest request = FlashcardRequest.builder()
                .deckId(null)
                .term("banana")
                .build();

        Flashcard mappedFlashcard = new Flashcard();
        Flashcard savedFlashcard = Flashcard.builder().id(11L).term("banana").build();
        FlashcardResponse response = new FlashcardResponse();
        response.setId(11L);

        when(flashcardMapper.toEntity(request)).thenReturn(mappedFlashcard);
        when(flashcardRepository.save(mappedFlashcard)).thenReturn(savedFlashcard);
        when(flashcardMapper.toResponse(savedFlashcard)).thenReturn(response);

        ArgumentCaptor<UserFlashcardProgress> progressCaptor = ArgumentCaptor.forClass(UserFlashcardProgress.class);

        FlashcardResponse actual = flashcardService.createFlashcard(request);

        assertNotNull(actual);
        assertEquals(11L, actual.getId());
        assertNull(mappedFlashcard.getDeckFlashcards());

        verify(deckRepository, never()).findById(anyLong());
        verify(flashcardRepository).save(mappedFlashcard);
        verify(progressRepository).save(progressCaptor.capture());

        UserFlashcardProgress savedProgress = progressCaptor.getValue();
        assertEquals(currentUser.getId(), savedProgress.getUserId());
        assertSame(savedFlashcard, savedProgress.getFlashcard());
        assertEquals(2.5, savedProgress.getEaseFactor(), 0.000001);
        assertEquals(0, savedProgress.getRepetitions());
        assertEquals(0, savedProgress.getIntervalDays());
        assertNull(savedProgress.getNextReviewAt());
    }

    @Test
    void updateFlashcard_shouldThrowRuntimeException_whenFlashcardNotFound() {
        FlashcardRequest request = FlashcardRequest.builder().term("new").build();
        when(flashcardRepository.findById(1L)).thenReturn(Optional.empty());

        RuntimeException exception = assertThrows(RuntimeException.class, () -> flashcardService.updateFlashcard(1L, request));

        assertEquals("Flashcard not found with id: 1", exception.getMessage());
        verify(flashcardRepository).findById(1L);
        verify(flashcardRepository, never()).save(any());
    }

    @Test
    void updateFlashcard_shouldUpdateAndReturnResponse_whenFlashcardExists() {
        FlashcardRequest request = FlashcardRequest.builder()
                .term("new-term")
                .phonetic("new-phonetic")
                .definition("new-definition")
                .partOfSpeech("verb")
                .exampleSentence("new example")
                .build();

        Flashcard flashcard = Flashcard.builder()
                .id(5L)
                .term("old")
                .phonetic("old")
                .definition("old")
                .partOfSpeech("noun")
                .exampleSentence("old")
                .build();

        FlashcardResponse response = new FlashcardResponse();
        response.setId(5L);
        response.setTerm("new-term");

        when(flashcardRepository.findById(5L)).thenReturn(Optional.of(flashcard));
        when(flashcardRepository.save(flashcard)).thenReturn(flashcard);
        when(flashcardMapper.toResponse(flashcard)).thenReturn(response);

        FlashcardResponse actual = flashcardService.updateFlashcard(5L, request);

        assertNotNull(actual);
        assertEquals("new-term", actual.getTerm());
        assertEquals("new-term", flashcard.getTerm());
        assertEquals("new-phonetic", flashcard.getPhonetic());
        assertEquals("new-definition", flashcard.getDefinition());
        assertEquals("verb", flashcard.getPartOfSpeech());
        assertEquals("new example", flashcard.getExampleSentence());

        verify(flashcardRepository).findById(5L);
        verify(flashcardRepository).save(flashcard);
        verify(flashcardMapper).toResponse(flashcard);
    }

    @Test
    void deleteFlashcard_shouldThrowRuntimeException_whenFlashcardNotFound() {
        when(flashcardRepository.existsById(6L)).thenReturn(false);

        RuntimeException exception = assertThrows(RuntimeException.class, () -> flashcardService.deleteFlashcard(6L));

        assertEquals("Flashcard not found with id: 6", exception.getMessage());
        verify(flashcardRepository).existsById(6L);
        verify(flashcardRepository, never()).deleteById(anyLong());
    }

    @Test
    void deleteFlashcard_shouldDeleteFlashcard_whenFlashcardExists() {
        when(flashcardRepository.existsById(6L)).thenReturn(true);

        flashcardService.deleteFlashcard(6L);

        verify(flashcardRepository).existsById(6L);
        verify(flashcardRepository).deleteById(6L);
    }

    @Test
    void getFlashcardsByDeckId_shouldReturnMappedResponses() {
        Flashcard flashcard1 = Flashcard.builder().id(1L).term("one").build();
        Flashcard flashcard2 = Flashcard.builder().id(2L).term("two").build();

        FlashcardResponse response1 = new FlashcardResponse();
        response1.setId(1L);
        FlashcardResponse response2 = new FlashcardResponse();
        response2.setId(2L);

        when(flashcardRepository.findByDeckId(9L)).thenReturn(List.of(flashcard1, flashcard2));
        when(flashcardMapper.toResponse(flashcard1)).thenReturn(response1);
        when(flashcardMapper.toResponse(flashcard2)).thenReturn(response2);

        List<FlashcardResponse> actual = flashcardService.getFlashcardsByDeckId(9L);

        assertNotNull(actual);
        assertEquals(2, actual.size());
        assertEquals(1L, actual.get(0).getId());
        assertEquals(2L, actual.get(1).getId());

        verify(flashcardRepository).findByDeckId(9L);
        verify(flashcardMapper).toResponse(flashcard1);
        verify(flashcardMapper).toResponse(flashcard2);
    }

    @Test
    void createFlashcardsFromListWords_shouldThrowRuntimeException_whenDeckNotFound() {
        BulkFlashcardRequest request = new BulkFlashcardRequest();
        request.setDeckId(7L);
        request.setWord(List.of("alpha"));

        when(deckRepository.findById(7L)).thenReturn(Optional.empty());

        RuntimeException exception = assertThrows(RuntimeException.class,
                () -> flashcardService.createFlashcardsFromListWords(request));

        assertEquals("Deck not found with id: 7", exception.getMessage());
        verify(deckRepository).findById(7L);
        verify(aiService, never()).fetchFlashcards(anyList());
        verify(flashcardRepository, never()).saveAll(anyList());
        verify(progressRepository, never()).saveAll(anyList());
    }

    @Test
    void createFlashcardsFromListWords_shouldSaveAllAndCreateProgressWithDeckOwnerId_whenAiReturnsList() {
        BulkFlashcardRequest request = new BulkFlashcardRequest();
        request.setDeckId(8L);
        request.setWord(List.of("alpha", "beta"));

        Deck deck = Deck.builder().id(8L).ownerId(555L).build();

        BulkFlashcardResponse aiCard1 = bulkResponse("alpha", "pho1", "def1", "noun", "ex1");
        BulkFlashcardResponse aiCard2 = bulkResponse("beta", "pho2", "def2", "verb", "ex2");

        Flashcard saved1 = Flashcard.builder().id(21L).term("alpha").build();
        Flashcard saved2 = Flashcard.builder().id(22L).term("beta").build();

        FlashcardResponse response1 = new FlashcardResponse();
        response1.setId(21L);
        FlashcardResponse response2 = new FlashcardResponse();
        response2.setId(22L);

        when(deckRepository.findById(8L)).thenReturn(Optional.of(deck));
        when(aiService.fetchFlashcards(request.getWord())).thenReturn(List.of(aiCard1, aiCard2));
        when(flashcardRepository.saveAll(anyList())).thenReturn(List.of(saved1, saved2));
        when(flashcardMapper.toResponse(saved1)).thenReturn(response1);
        when(flashcardMapper.toResponse(saved2)).thenReturn(response2);

        ArgumentCaptor<List<Flashcard>> flashcardCaptor = ArgumentCaptor.forClass(List.class);
        ArgumentCaptor<List<UserFlashcardProgress>> progressCaptor = ArgumentCaptor.forClass(List.class);

        List<FlashcardResponse> actual = flashcardService.createFlashcardsFromListWords(request);

        assertNotNull(actual);
        assertEquals(2, actual.size());

        verify(flashcardRepository, times(1)).saveAll(flashcardCaptor.capture());
        List<Flashcard> flashcardsToSave = flashcardCaptor.getValue();
        assertEquals(2, flashcardsToSave.size());

        Flashcard first = flashcardsToSave.get(0);
        assertEquals("alpha", first.getTerm());
        assertEquals("pho1", first.getPhonetic());
        assertEquals("def1", first.getDefinition());
        assertEquals("noun", first.getPartOfSpeech());
        assertEquals("ex1", first.getExampleSentence());
        assertNotNull(first.getDeckFlashcards());
        assertEquals(1, first.getDeckFlashcards().size());
        assertSame(deck, first.getDeckFlashcards().get(0).getDeck());
        assertSame(first, first.getDeckFlashcards().get(0).getFlashcard());

        verify(progressRepository, times(1)).saveAll(progressCaptor.capture());
        List<UserFlashcardProgress> progresses = progressCaptor.getValue();
        assertEquals(2, progresses.size());
        for (int i = 0; i < progresses.size(); i++) {
            UserFlashcardProgress progress = progresses.get(i);
            assertEquals(555L, progress.getUserId());
            assertEquals(2.5, progress.getEaseFactor(), 0.000001);
            assertEquals(0, progress.getRepetitions());
            assertEquals(0, progress.getIntervalDays());
            assertNull(progress.getNextReviewAt());
        }
    }

    @Test
    void createFlashcardsFromListWords_shouldSaveEmptyListAndCreateNoProgressItems_whenAiReturnsEmptyList() {
        BulkFlashcardRequest request = new BulkFlashcardRequest();
        request.setDeckId(9L);
        request.setWord(List.of("none"));

        Deck deck = Deck.builder().id(9L).ownerId(444L).build();

        when(deckRepository.findById(9L)).thenReturn(Optional.of(deck));
        when(aiService.fetchFlashcards(request.getWord())).thenReturn(List.of());
        when(flashcardRepository.saveAll(anyList())).thenReturn(List.of());

        ArgumentCaptor<List<Flashcard>> flashcardCaptor = ArgumentCaptor.forClass(List.class);
        ArgumentCaptor<List<UserFlashcardProgress>> progressCaptor = ArgumentCaptor.forClass(List.class);

        List<FlashcardResponse> actual = flashcardService.createFlashcardsFromListWords(request);

        assertNotNull(actual);
        assertTrue(actual.isEmpty());

        verify(flashcardRepository, times(1)).saveAll(flashcardCaptor.capture());
        assertTrue(flashcardCaptor.getValue().isEmpty());

        verify(progressRepository, times(1)).saveAll(progressCaptor.capture());
        assertTrue(progressCaptor.getValue().isEmpty());

        verify(flashcardMapper, never()).toResponse(any());
    }

    @Test
    void addFlashcardsFromListWords_shouldCreateNewStudyVocabularyDeck_whenDeckDoesNotExist() {
        AddFlashcardRequest request = new AddFlashcardRequest();
        request.setWords(List.of("apple"));

        when(deckRepository.findByOwnerIdAndName(currentUser.getId(), "Study Vocabulary")).thenReturn(Optional.empty());

        Deck createdDeck = Deck.builder()
                .id(30L)
                .name("Study Vocabulary")
                .description("Vocabulary collected from lessons and reading activities during your study.")
                .ownerId(currentUser.getId())
                .creatorId(currentUser.getId())
                .sourceDeckId(null)
                .build();

        BulkFlashcardResponse aiCard = bulkResponse("apple", "pho", "def", "noun", "ex");
        Flashcard savedFlashcard = Flashcard.builder().id(31L).term("apple").build();
        FlashcardResponse response = new FlashcardResponse();
        response.setId(31L);

        when(deckRepository.save(any(Deck.class))).thenReturn(createdDeck);
        when(aiService.fetchFlashcards(request.getWords())).thenReturn(List.of(aiCard));
        when(flashcardRepository.saveAll(anyList())).thenReturn(List.of(savedFlashcard));
        when(flashcardMapper.toResponse(savedFlashcard)).thenReturn(response);

        ArgumentCaptor<Deck> deckCaptor = ArgumentCaptor.forClass(Deck.class);
        ArgumentCaptor<List<UserFlashcardProgress>> progressCaptor = ArgumentCaptor.forClass(List.class);

        List<FlashcardResponse> actual = flashcardService.addFlashcardsFromListWords(request);

        assertNotNull(actual);
        assertEquals(1, actual.size());

        verify(deckRepository).save(deckCaptor.capture());
        Deck savedDeck = deckCaptor.getValue();
        assertEquals("Study Vocabulary", savedDeck.getName());
        assertEquals(currentUser.getId(), savedDeck.getOwnerId());
        assertEquals(currentUser.getId(), savedDeck.getCreatorId());
        assertNull(savedDeck.getSourceDeckId());

        verify(flashcardRepository, times(1)).saveAll(anyList());
        verify(progressRepository).saveAll(progressCaptor.capture());

        List<UserFlashcardProgress> progresses = progressCaptor.getValue();
        assertEquals(1, progresses.size());
        assertEquals(currentUser.getId(), progresses.get(0).getUserId());
        assertEquals(2.5, progresses.get(0).getEaseFactor(), 0.000001);
        assertEquals(0, progresses.get(0).getRepetitions());
        assertEquals(0, progresses.get(0).getIntervalDays());
        assertNull(progresses.get(0).getNextReviewAt());
    }

    @Test
    void addFlashcardsFromListWords_shouldReuseExistingDeck_whenStudyVocabularyDeckExists() {
        AddFlashcardRequest request = new AddFlashcardRequest();
        request.setWords(List.of("reuse1", "reuse2"));

        Deck existingDeck = Deck.builder()
                .id(40L)
                .name("Study Vocabulary")
                .ownerId(currentUser.getId())
                .creatorId(currentUser.getId())
                .build();

        BulkFlashcardResponse aiCard1 = bulkResponse("reuse1", "pho1", "def1", "noun", "ex1");
        BulkFlashcardResponse aiCard2 = bulkResponse("reuse2", "pho2", "def2", "verb", "ex2");

        Flashcard saved1 = Flashcard.builder().id(41L).term("reuse1").build();
        Flashcard saved2 = Flashcard.builder().id(42L).term("reuse2").build();

        FlashcardResponse response1 = new FlashcardResponse();
        response1.setId(41L);
        FlashcardResponse response2 = new FlashcardResponse();
        response2.setId(42L);

        when(deckRepository.findByOwnerIdAndName(currentUser.getId(), "Study Vocabulary")).thenReturn(Optional.of(existingDeck));
        when(aiService.fetchFlashcards(request.getWords())).thenReturn(List.of(aiCard1, aiCard2));
        when(flashcardRepository.saveAll(anyList())).thenReturn(List.of(saved1, saved2));
        when(flashcardMapper.toResponse(saved1)).thenReturn(response1);
        when(flashcardMapper.toResponse(saved2)).thenReturn(response2);

        ArgumentCaptor<List<Flashcard>> flashcardCaptor = ArgumentCaptor.forClass(List.class);
        ArgumentCaptor<List<UserFlashcardProgress>> progressCaptor = ArgumentCaptor.forClass(List.class);

        List<FlashcardResponse> actual = flashcardService.addFlashcardsFromListWords(request);

        assertNotNull(actual);
        assertEquals(2, actual.size());

        verify(deckRepository, never()).save(any(Deck.class));
        verify(flashcardRepository, times(1)).saveAll(flashcardCaptor.capture());

        List<Flashcard> flashcards = flashcardCaptor.getValue();
        assertEquals(2, flashcards.size());
        for (Flashcard flashcard : flashcards) {
            assertNotNull(flashcard.getDeckFlashcards());
            assertEquals(1, flashcard.getDeckFlashcards().size());
            assertSame(existingDeck, flashcard.getDeckFlashcards().get(0).getDeck());
            assertSame(flashcard, flashcard.getDeckFlashcards().get(0).getFlashcard());
        }

        verify(progressRepository).saveAll(progressCaptor.capture());
        List<UserFlashcardProgress> progresses = progressCaptor.getValue();
        assertEquals(2, progresses.size());
        assertEquals(currentUser.getId(), progresses.get(0).getUserId());
        assertEquals(currentUser.getId(), progresses.get(1).getUserId());
    }

    private BulkFlashcardResponse bulkResponse(String term, String phonetic, String definition, String partOfSpeech, String example) {
        BulkFlashcardResponse response = new BulkFlashcardResponse();
        response.setTerm(term);
        response.setPhonetic(phonetic);
        response.setDefinition(definition);
        response.setPartOfSpeech(partOfSpeech);
        response.setExampleSentence(example);
        return response;
    }
}
