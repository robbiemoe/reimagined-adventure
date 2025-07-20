// Card definitions
const suits = ['♠', '♥', '♦', '♣']; // Changed suit characters for broader compatibility
const values = ['2', '3', '4', '5', '6', '7', '8', '9', '10', 'J', 'Q', 'K', 'A'];
const suitColors = {
    '♠': 'black',
    '♥': 'red',
    '♦': 'red',
    '♣': 'black'
};

// Helper to map card values to numerical ranks for easier comparison
const cardRankMap = {
    '2': 2, '3': 3, '4': 4, '5': 5, '6': 6, '7': 7, '8': 8, '9': 9, '10': 10,
    'J': 11, 'Q': 12, 'K': 13, 'A': 14
};

// Game state
let deck = [];
let communityCards = [];
let playerHands = [[], [], []];
let winningPlayerIndex = -1;
let score = 0;
let totalQuestions = 0;
let timer = null; // Main game timer
let countdownTimer = null; // Timer for each hand
let startTime = 0; // Game start time
let handStartTime = 0; // Current hand start time
let timeLimit = 20; // Default time limit in seconds
let playerName = "";
let playerId = "";
let gameActive = false;
let handTimes = []; // To store time taken for each hand
let handResults = []; // To store correctness for each hand
let handTypes = []; // To store winning hand type for history
let timeoutId = null; // To store the timeout ID for a hand, allowing it to be cleared

// Initialize the game
function initGame() {
    document.getElementById('restart-btn').addEventListener('click', showRegistrationModal);
    document.getElementById('next-btn').addEventListener('click', nextHand);
    document.getElementById('registration-form').addEventListener('submit', handleRegistration);

    // Set default level indicator on load
    updateLevelIndicator(20); // 'Good' level by default
    showRegistrationModal(); // Show registration on first load
}

// Show registration modal
function showRegistrationModal() {
    document.getElementById('registration-modal').classList.remove('hidden');
    // Stop any active game timers if modal is reopened during a game
    if (timer) clearInterval(timer);
    if (countdownTimer) clearInterval(countdownTimer);
    if (timeoutId) clearTimeout(timeoutId);
    gameActive = false; // Ensure game is not active
}

// Handle registration form submission
function handleRegistration(e) {
    e.preventDefault();

    playerName = document.getElementById('player-name').value.trim();
    playerId = document.getElementById('player-id').value.trim();
    timeLimit = parseInt(document.getElementById('difficulty-level').value);

    if (playerName && playerId) {
        document.getElementById('registration-modal').classList.add('hidden');
        document.getElementById('player-info').textContent = `${playerName} (ID: ${playerId})`;
        updateLevelIndicator(timeLimit);
        startGame();
    }
}

// Update level indicator display
function updateLevelIndicator(seconds) {
    const levelIndicator = document.getElementById('level-indicator');
    let levelText = "";
    let bgColor = "";
    let textColor = "";

    switch(seconds) {
        case 10:
            levelText = "Excellent";
            bgColor = "bg-yellow-100";
            textColor = "text-yellow-800";
            break;
        case 15:
            levelText = "Professional";
            bgColor = "bg-purple-100";
            textColor = "text-purple-800";
            break;
        default: // Covers 20 seconds and any other unexpected value
            levelText = "Good";
            bgColor = "bg-blue-100";
            textColor = "text-blue-800";
    }

    levelIndicator.textContent = levelText;
    levelIndicator.className = `level-badge ${bgColor} ${textColor}`; // Apply new classes
}

// Start the entire game session
function startGame() {
    score = 0;
    totalQuestions = 0;
    handTimes = [];
    handResults = [];
    handTypes = [];
    gameActive = true;

    updateScore();
    document.getElementById('restart-btn').classList.add('hidden');
    document.getElementById('next-btn').classList.remove('hidden');
    document.querySelector('.progress').style.width = '0%';
    document.getElementById('game-stats').classList.add('hidden'); // Hide stats
    // Remove the manual data instruction if it exists from previous game
    const oldInstruction = document.querySelector('.manual-data-instruction');
    if (oldInstruction) oldInstruction.remove();

    document.getElementById('history-table-body').innerHTML = ''; // Clear history table

    startTimer(); // Start the main game timer
    dealNewHand(); // Deal the first hand
}

// Deal a new hand
function dealNewHand() {
    resetDeck();
    communityCards = dealCards(5);
    playerHands = [dealCards(2), dealCards(2), dealCards(2)]; // Three players

    // Display community cards
    displayCard('flop1', communityCards[0]);
    displayCard('flop2', communityCards[1]);
    displayCard('flop3', communityCards[2]);
    displayCard('turn', communityCards[3]);
    displayCard('river', communityCards[4]);

    // Display player cards
    displayCard('player1-card1', playerHands[0][0]);
    displayCard('player1-card2', playerHands[0][1]);
    displayCard('player2-card1', playerHands[1][0]);
    displayCard('player2-card2', playerHands[1][1]);
    displayCard('player3-card1', playerHands[2][0]);
    displayCard('player3-card2', playerHands[2][1]);

    // Evaluate hands and determine winner using Texas Hold'em logic
    const handRankings = playerHands.map((hand, index) => {
        const handRank = evaluateTexasHoldemHand(hand, communityCards);
        return { index, handRank };
    });

    // Sort to find the winner(s) - handling ties at the highest rank
    handRankings.sort((a, b) => {
        if (b.handRank.rank !== a.handRank.rank) {
            return b.handRank.rank - a.handRank.rank;
        }
        for (let i = 0; i < Math.min(a.handRank.highCards.length, b.handRank.highCards.length); i++) {
            if (b.handRank.highCards[i] !== a.handRank.highCards[i]) {
                return b.handRank.highCards[i] - a.handRank.highCards[i];
            }
        }
        return 0; // True tie
    });

    // Determine the single winning player for this game
    // If multiple players have the same highest hand, the one with the highest highCard(s) wins.
    // If all highCards are also equal, it's a split pot. For this game, we pick the first one from the sorted list as THE winner.
    winningPlayerIndex = handRankings[0].index;
    
    // Store the winning hand type for history
    handTypes.push(handRankings[0].handRank.name);

    // Set hand descriptions for display later
    for (let i = 0; i < 3; i++) {
        const handElement = document.getElementById(`player${i+1}-hand`);
        handElement.textContent = handRankings.find(h => h.index === i).handRank.name;
        handElement.classList.add('hidden'); // Keep hidden until answered
    }

    // Hide previous result message
    const resultElement = document.getElementById('result');
    resultElement.classList.add('hidden');

    // Remove any highlight from previous hand
    document.querySelectorAll('.highlight').forEach(el => {
        el.classList.remove('highlight');
    });

    // Enable all player selection buttons
    const playerButtons = document.querySelectorAll('.grid .btn'); // Select buttons specifically within player sections
    playerButtons.forEach(btn => {
        btn.disabled = false;
    });

    totalQuestions++;
    handStartTime = Date.now(); // Reset hand start time for countdown

    // Start countdown timer for this hand
    startCountdownTimer();
}

// Start countdown timer for each hand
function startCountdownTimer() {
    if (countdownTimer) clearInterval(countdownTimer); // Clear any existing interval
    if (timeoutId) clearTimeout(timeoutId); // Clear any previous safety timeout

    const timerElement = document.querySelector('.timer');
    let timeRemaining = timeLimit;

    timerElement.textContent = formatTime(timeRemaining);
    timerElement.classList.remove('time-warning'); // Remove warning from previous hand if any

    countdownTimer = setInterval(() => {
        timeRemaining--;
        timerElement.textContent = formatTime(timeRemaining);

        if (timeRemaining <= 5 && timeRemaining > 0) { // Apply warning only when time is low and not zero
            timerElement.classList.add('time-warning');
        } else {
            timerElement.classList.remove('time-warning'); // Remove if time goes up or to 0
        }

        if (timeRemaining <= 0) {
            clearInterval(countdownTimer);
            timeoutAnswer(); // Call timeout handler when time runs out
        }
    }, 1000);

    // Set a safety timeout to ensure timeoutAnswer is called even if interval is delayed
    timeoutId = setTimeout(() => {
        if (gameActive && document.querySelectorAll('.grid .btn:not([disabled])').length > 0) {
            // Only call if game is active and player buttons are still enabled
            timeoutAnswer();
        }
    }, timeLimit * 1000 + 100); // Small buffer to ensure it runs after interval check
}

// Format time as MM:SS
function formatTime(seconds) {
    const mins = Math.floor(seconds / 60).toString().padStart(2, '0');
    const secs = (seconds % 60).toString().padStart(2, '0');
    return `${mins}:${secs}`;
}

// Handle timeout (no answer selected)
function timeoutAnswer() {
    // Check if the hand has already been resolved by a player selection
    if (document.querySelectorAll('.grid .btn:not([disabled])').length === 0) {
        return; // If all player selection buttons are disabled, an answer was already chosen
    }

    clearInterval(countdownTimer); // Stop the countdown
    clearTimeout(timeoutId); // Clear the safety timeout

    const timeTaken = (Date.now() - handStartTime) / 1000;
    handTimes.push(timeTaken);
    handResults.push(false); // Mark as incorrect due to timeout

    const resultElement = document.getElementById('result');
    resultElement.textContent = `Time's up! Player ${winningPlayerIndex + 1} has the winning hand (${handTypes[handTypes.length - 1]}).`;
    resultElement.className = "p-4 rounded-lg mb-6 text-center bg-orange-100 text-orange-800 font-bold text-xl";
    resultElement.classList.remove('hidden');

    // Show all player hand types
    for (let i = 0; i < 3; i++) {
        document.getElementById(`player${i+1}-hand`).classList.remove('hidden');
    }

    // Highlight the actual winning player's cards
    const winningContainer = document.getElementById(`player${winningPlayerIndex + 1}-container`);
    winningContainer.querySelectorAll('.card').forEach(card => {
        card.classList.add('highlight');
    });

    // Disable player selection buttons after timeout
    const playerButtons = document.querySelectorAll('.grid .btn');
    playerButtons.forEach(btn => {
        btn.disabled = true;
    });

    updateProgress();
    updateHistory();
}

// Reset and create a new deck
function resetDeck() {
    deck = [];
    for (let suit of suits) {
        for (let value of values) {
            deck.push({ suit, value });
        }
    }
    // Shuffle the deck (Fisher-Yates shuffle)
    for (let i = deck.length - 1; i > 0; i--) {
        const j = Math.floor(Math.random() * (i + 1));
        [deck[i], deck[j]] = [deck[j], deck[i]];
    }
}

// Deal cards from the deck
function dealCards(count) {
    return deck.splice(0, count);
}

// Display a card on the UI
function displayCard(elementId, card) {
    const cardElement = document.getElementById(elementId);
    const colorClass = suitColors[card.suit];

    cardElement.innerHTML = `
        <div class="flex flex-col items-center justify-center h-full ${colorClass}">
            <div class="card-value">${card.value}</div>
            <div class="card-suit">${card.suit}</div>
        </div>
    `;
}

// --- Texas Hold'em Hand Evaluation Logic (as provided, with minor adjustments) ---

// Function to generate all combinations of k elements from an array
function getCombinations(arr, k) {
    const result = [];
    function backtrack(startIndex, currentCombination) {
        if (currentCombination.length === k) {
            result.push([...currentCombination]);
            return;
        }
        for (let i = startIndex; i < arr.length; i++) {
            currentCombination.push(arr[i]);
            backtrack(i + 1, currentCombination);
            currentCombination.pop();
        }
    }
    backtrack(0, []);
    return result;
}

// Evaluate a single 5-card hand
function evaluateFiveCardHand(fiveCards) {
    // Sort cards by numerical value (descending)
    const sortedCards = fiveCards.sort((a, b) => cardRankMap[b.value] - cardRankMap[a.value]);

    const valueCounts = {}; // { numericValue: count }
    const suitCounts = {};  // { suit: count }
    const numericValues = []; // Array of numerical values of the cards (for kickers, high card)

    for (const card of sortedCards) {
        const numericValue = cardRankMap[card.value];
        valueCounts[numericValue] = (valueCounts[numericValue] || 0) + 1;
        suitCounts[card.suit] = (suitCounts[card.suit] || 0) + 1;
        numericValues.push(numericValue);
    }

    // Check for Flush
    let isFlush = false;
    let flushSuit = '';
    for (const suit in suitCounts) {
        if (suitCounts[suit] >= 5) {
            isFlush = true;
            flushSuit = suit;
            break;
        }
    }

    // Check for Straight
    let isStraight = false;
    let straightHighCard = 0;
    const uniqueNumericValues = Array.from(new Set(numericValues)).sort((a, b) => a - b); // Ascending for straight check

    // Handle Ace-low straight (A, 2, 3, 4, 5) where Ace is 14 but can act as 1
    if (uniqueNumericValues.includes(14) && uniqueNumericValues.includes(2) &&
        uniqueNumericValues.includes(3) && uniqueNumericValues.includes(4) &&
        uniqueNumericValues.includes(5)) {
        isStraight = true;
        straightHighCard = 5; // The highest card in A-5 straight (Ace acts as 1)
    } else {
        // Check for regular straights
        for (let i = 0; i <= uniqueNumericValues.length - 5; i++) {
            if (uniqueNumericValues[i + 4] - uniqueNumericValues[i] === 4) {
                isStraight = true;
                straightHighCard = uniqueNumericValues[i + 4]; // Highest card of the straight
                break;
            }
        }
    }

    // Identify pairs, three of a kind, four of a kind
    // Create arrays of values based on their counts, sorted descending by value
    const getValuesByCount = (count) => Object.keys(valueCounts)
        .map(Number)
        .filter(val => valueCounts[val] === count)
        .sort((a, b) => b - a);

    const quads = getValuesByCount(4);
    const trips = getValuesByCount(3);
    const pairs = getValuesByCount(2);
    const singles = getValuesByCount(1); // Kickers

    // Determine hand rank based on poker hierarchy
    if (isStraight && isFlush) {
        // Check for Royal Flush (10-J-Q-K-A of the same suit)
        if (straightHighCard === 14 && 
            fiveCards.filter(c => c.suit === flushSuit && ['10', 'J', 'Q', 'K', 'A'].includes(c.value)).length === 5) {
             return { rank: 9, name: "Royal Flush", highCards: [14] };
        }
        return { rank: 8, name: "Straight Flush", highCards: [straightHighCard] };
    } else if (quads.length > 0) {
        // Four of a Kind: quad value, then highest kicker
        return { rank: 7, name: "Four of a Kind", highCards: [quads[0], ...singles.slice(0,1)] }; 
    } else if (trips.length > 0 && pairs.length > 0) {
        // Full House: highest trips value, then highest pair value
        return { rank: 6, name: "Full House", highCards: [trips[0], pairs[0]] };
    } else if (isFlush) {
        // Flush: values of the 5 flush cards, sorted descending
        const flushCardsNumericValues = sortedCards.filter(c => c.suit === flushSuit).map(c => cardRankMap[c.value]);
        return { rank: 5, name: "Flush", highCards: flushCardsNumericValues.slice(0, 5) };
    } else if (isStraight) {
        return { rank: 4, name: "Straight", highCards: [straightHighCard] };
    } else if (trips.length > 0) {
        // Three of a Kind: trips value, then two highest kickers
        return { rank: 3, name: "Three of a Kind", highCards: [trips[0], ...singles.slice(0, 2)] };
    } else if (pairs.length >= 2) {
        // Two Pair: highest pair value, then second highest pair value, then one kicker
        return { rank: 2, name: "Two Pair", highCards: [pairs[0], pairs[1], ...singles.slice(0, 1)] };
    } else if (pairs.length === 1) {
        // One Pair: pair value, then three highest kickers
        return { rank: 1, name: "One Pair", highCards: [pairs[0], ...singles.slice(0, 3)] };
    } else {
        // High Card: five highest kickers
        return { rank: 0, name: "High Card", highCards: numericValues.slice(0, 5) };
    }
}

// Main evaluation function for Texas Hold'em (best 5 out of 7 cards)
function evaluateTexasHoldemHand(holeCards, communityCards) {
    const allSevenCards = [...holeCards, ...communityCards];
    const allFiveCardCombinations = getCombinations(allSevenCards, 5);

    let bestHand = { rank: -1, name: "No Hand", highCards: [] }; // Initialize with lowest possible rank

    for (const fiveCardCombo of allFiveCardCombinations) {
        const currentHandRank = evaluateFiveCardHand(fiveCardCombo);

        // Compare current hand with the best found so far
        if (currentHandRank.rank > bestHand.rank) {
            bestHand = currentHandRank;
        } else if (currentHandRank.rank === bestHand.rank) {
            // Tie-breaking for hands of the same rank
            let currentHandIsBetter = false;
            for (let i = 0; i < currentHandRank.highCards.length; i++) {
                if (currentHandRank.highCards[i] > bestHand.highCards[i]) {
                    currentHandIsBetter = true;
                    break;
                } else if (currentHandRank.highCards[i] < bestHand.highCards[i]) {
                    currentHandIsBetter = false;
                    break;
                }
            }
            if (currentHandIsBetter) {
                bestHand = currentHandRank;
            }
        }
    }
    return bestHand;
}

// Select a winner
function selectWinner(playerIndex) {
    // Clear the countdown timer and its safety timeout
    if (countdownTimer) clearInterval(countdownTimer);
    if (timeoutId) clearTimeout(timeoutId);
    
    // Record time taken
    const timeTaken = (Date.now() - handStartTime) / 1000;
    handTimes.push(timeTaken);
    
    // Disable all player selection buttons
    const playerButtons = document.querySelectorAll('.grid .btn');
    playerButtons.forEach(btn => {
        btn.disabled = true;
    });
    
    // Show all hand types for comparison
    for (let i = 0; i < 3; i++) {
        document.getElementById(`player${i+1}-hand`).classList.remove('hidden');
    }
    
    // Show result message
    const resultElement = document.getElementById('result');
    resultElement.classList.remove('hidden');
    
    if (playerIndex === winningPlayerIndex) {
        resultElement.textContent = "Correct! Well done!";
        resultElement.className = "p-4 rounded-lg mb-6 text-center bg-green-100 text-green-800 font-bold text-xl";
        score++;
        handResults.push(true); // Mark as correct
    } else {
        resultElement.textContent = `Incorrect. Player ${winningPlayerIndex + 1} has the winning hand (${handTypes[handTypes.length - 1]}).`;
        resultElement.className = "p-4 rounded-lg mb-6 text-center bg-red-100 text-red-800 font-bold text-xl";
        handResults.push(false); // Mark as incorrect
    }
    
    // Highlight the winning player's cards
    const winningContainer = document.getElementById(`player${winningPlayerIndex + 1}-container`);
    winningContainer.querySelectorAll('.card').forEach(card => {
        card.classList.add('highlight');
    });
    
    updateScore();
    updateProgress();
    updateHistory(); // Update the history table after each hand
}

// Update the score display
function updateScore() {
    document.getElementById('score').textContent = score;
    document.getElementById('total').textContent = totalQuestions;
}

// Update progress bar
function updateProgress() {
    const progressPercent = totalQuestions > 0 ? (score / totalQuestions) * 100 : 0;
    document.querySelector('.progress').style.width = `${progressPercent}%`;
}

// Update history table
function updateHistory() {
    const tableBody = document.getElementById('history-table-body');
    const index = handResults.length - 1; // Current hand index

    if (index >= 0) {
        const row = document.createElement('tr');
        row.innerHTML = `
            <td class="py-2 px-4 border-b border-gray-200">${index + 1}</td>
            <td class="py-2 px-4 border-b border-gray-200">
                <span class="px-2 py-1 rounded-full text-xs ${handResults[index] ? 'bg-green-100 text-green-800' : 'bg-red-100 text-red-800'}">
                    ${handResults[index] ? 'Correct' : 'Incorrect'}
                </span>
            </td>
            <td class="py-2 px-4 border-b border-gray-200">${handTimes[index].toFixed(1)}</td>
            <td class="py-2 px-4 border-b border-gray-200">${handTypes[index]}</td>
        `;
        tableBody.appendChild(row);
    }
}

// Move to the next hand or end the game
function nextHand() {
    // Example: End after 10 questions for a "session"
    if (totalQuestions >= 10) { 
        endGame();
    } else {
        dealNewHand();
    }
}

// End the game and show statistics
function endGame() {
    gameActive = false;
    
    // Stop all active timers
    if (timer) clearInterval(timer);
    if (countdownTimer) clearInterval(countdownTimer);
    if (timeoutId) clearTimeout(timeoutId);
    
    // Calculate statistics
    const accuracy = totalQuestions > 0 ? (score / totalQuestions) * 100 : 0;
    const avgTime = handTimes.length > 0 ? handTimes.reduce((a, b) => a + b, 0) / handTimes.length : 0;
    
    // Determine performance rating
    let rating = "Beginner";
    if (accuracy >= 90) {
        if (avgTime < timeLimit * 0.5) {
            rating = "Expert";
        } else if (avgTime < timeLimit * 0.7) {
            rating = "Advanced";
        } else {
            rating = "Proficient";
        }
    } else if (accuracy >= 70) {
        if (avgTime < timeLimit * 0.6) {
            rating = "Proficient";
        } else {
            rating = "Intermediate";
        }
    } else if (accuracy >= 50) {
        rating = "Intermediate";
    }
    
    // Update stats display
    document.getElementById('accuracy-stat').textContent = `${accuracy.toFixed(1)}%`;
    document.getElementById('avg-time-stat').textContent = `${avgTime.toFixed(1)}s`;
    document.getElementById('rating-stat').textContent = rating;
    
    // Show stats section
    document.getElementById('game-stats').classList.remove('hidden');
    
    // Hide "Next Hand" and show "Play Again" button
    document.getElementById('next-btn').classList.add('hidden');
    document.getElementById('restart-btn').classList.remove('hidden');
    document.getElementById('restart-btn').textContent = "Play Again";

    // --- Manual Data Collection Instruction ---
    // IMPORTANT: Replace "https://forms.office.com/r/dbACtrhL5U" with your actual Microsoft Forms link
    const msFormLink = "https://forms.office.com/r/dbACtrhL5U"; 
    const manualDataInstruction = `
        <div class="manual-data-instruction mt-8 p-4 bg-blue-100 text-blue-800 rounded-lg text-center font-semibold">
            <p class="text-lg mb-2">Please record your game results!</p>
            <p class="mb-4">Since this game runs directly from OneDrive, your scores need to be manually submitted.</p>
            <a href="${msFormLink}" target="_blank" class="bg-blue-600 hover:bg-blue-700 text-white font-bold py-2 px-5 rounded-lg inline-flex items-center justify-center transition duration-300">
                <svg class="w-5 h-5 mr-2" fill="currentColor" viewBox="0 0 20 20" xmlns="http://www.w3.org/2000/svg"><path fill-rule="evenodd" d="M10 18a8 8 0 100-16 8 8 0 000 16zm1-11a1 1 0 10-2 0v2H7a1 1 0 100 2h2v2a1 1 0 102 0v-2h2a1 1 0 100-2h-2V7z" clip-rule="evenodd"></path></svg>
                Open Submission Form
            </a>
            <p class="mt-4 text-sm text-gray-700">Please enter the following data:</p>
            <ul class="list-disc list-inside mt-2 text-left mx-auto max-w-sm">
                <li><strong>Player Name:</strong> ${playerName || 'N/A'}</li>
                <li><strong>Player ID:</strong> ${playerId || 'N/A'}</li>
                <li><strong>Difficulty:</strong> ${timeLimit} seconds (${document.getElementById('level-indicator').textContent})</li>
                <li><strong>Final Score:</strong> ${score} / ${totalQuestions}</li>
                <li><strong>Accuracy:</strong> ${accuracy.toFixed(1)}%</li>
                <li><strong>Average Time per Hand:</strong> ${avgTime.toFixed(1)}s</li>
                <li><strong>Performance Rating:</strong> ${rating}</li>
            </ul>
            <p class="mt-4 text-sm text-gray-700">You can also copy the "Session History" table data if requested.</p>
        </div>
    `;
    document.getElementById('game-stats').insertAdjacentHTML('afterend', manualDataInstruction);
}

// Main game timer (optional, if you want to track total session time)
function startTimer() {
    startTime = Date.now();
    if (timer) clearInterval(timer);
    
    timer = setInterval(() => {
        if (!gameActive) return; // Stop if game is not active
        
        const elapsedTime = Math.floor((Date.now() - startTime) / 1000);
        const minutes = Math.floor(elapsedTime / 60).toString().padStart(2, '0');
        const seconds = (elapsedTime % 60).toString().padStart(2, '0');
        document.querySelector('.timer').textContent = `${minutes}:${seconds}`;
    }, 1000);
}

// Initialize the game when the page loads
window.onload = initGame;