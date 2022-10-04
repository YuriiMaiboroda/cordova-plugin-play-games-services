interface IResponse {
    status: number;
    message: string;
}

interface IGooglePlayErrorResponse extends IResponse {
    googlePlayError: {
        errorCode: number;
        errorString: string;
    }
}

interface IAuthInput {
    silent?: boolean;
}

interface IAuthFailedResponse extends IResponse {
    userCancel: boolean;
}

interface ISignedInResponse extends IResponse {
    isSignedIn: boolean;
}

interface IShowPlayerResponse extends IResponse {
    displayName: string;
    playerId: string;
    title: string;
    iconImageUri: string;
    iconImageUrl: string;
    hiResIconImageUri: string;
    hiResIconImageUrl: string;
}

interface IGetPlayerScoreInput {
    leaderboardId: string;
}
interface IGetPlayerScoreResponse extends IResponse {
    playerScore: number;
    playerRank: number;
    isStale: boolean;
}

interface IShowLeaderboardInput {
    leaderboardId: string;
}

interface ISubmitScoreInput {
    score: number;
    leaderboardId: string;
}
interface ISubmitScoreResponse extends IResponse {
    leaderboardId: string;
    playerId: string;
    formattedScore: string;
    newBest: boolean;
    rawScore: number;
    scoreTag: string;
}

interface IUnlockAchievementInput {
    achievementId: string;
}
interface IUnlockAchievementResponse extends IResponse {
    achievementId: string;
}

interface IIncrementAchievementInput extends IResponse {
    achievementId: string;
    numSteps: number;
}
interface IIncrementAchievementResponse extends IResponse {
    achievementId: string;
    isUnlocked: boolean;
}

interface ISnapshotMetadata {
	saveTime: number;
	title: string;
	description: string;
	playedTime: number;
	progressValue: number;
	coverImageAspectRatio: number;
	coverImage: string;
	deviceName: string;
	playerId: string;
	playerDisplayName: string;
	playerName: string;
	playerTitle: string;
	playerIconImage: string;
	playerHiResImage: string;
}
interface ISnapshotConflictResponse extends IResponse {
	message: string;
	status: number;
	conflictId: string;
	serverData: string;
	serverMetadata: ISnapshotMetadata;
	localData: string;
	localMetadata: ISnapshotMetadata;
}

interface ISaveGameInput {
    saveName: string;
    saveData: string;
    previousSaveTime?: number;
    resolutionPolicy?: number;
}
interface ISaveGameResponse extends IResponse {
    metadata: ISnapshotMetadata;
}

interface ILoadGameInput {
    saveName: string;
}
interface ILoadGameResponse extends IResponse {
    saveData: string;
    metadata: ISnapshotMetadata;
}

interface IResolveSnapshotConflictInput {
    useLocal?: boolean;
}

interface IDeleteGameInput {
    saveName: string;
}

interface IPlayGamesServices {
    /**
     * Logs into google play services
     */
    auth(data: IAuthInput, onSignInSucceeded?: (response: IResponse) => void, onSignInFailed?: (response: IAuthFailedResponse | IResponse | string | IGooglePlayErrorResponse) => void): void;

    /**
     * Logs out from google play services
     */
    signOut(onSignOut?: () => void, onError?: (response: IResponse | string | IGooglePlayErrorResponse) => void): void;

    /**
     * Checks if the user is already logged in
     */
    isSignedIn(callback: (response: ISignedInResponse) => void, onError?: (response: IResponse | string | IGooglePlayErrorResponse) => void): void;

    /**
     * Fetch the currently authenticated player's data.
     */
    showPlayer(onSuccess: (response: IShowPlayerResponse) => void, onError?: (response: IResponse | string | IGooglePlayErrorResponse) => void): void;

    /**
     * Submits score to a leaderboard
     */
    submitScore(data: ISubmitScoreInput, onSuccess?: (response: IResponse) => void, onError?: (response: IResponse | string | IGooglePlayErrorResponse) => void): void;

    /**
     * Submits score to a leaderboard syncronously and wait for response
     */
    submitScoreNow(data: ISubmitScoreInput, onSuccess: (response: ISubmitScoreResponse) => void, onError?: (response: IResponse | string | IGooglePlayErrorResponse) => void): void;

    /**
     * Get player's score
     */
    getPlayerScore(data: IGetPlayerScoreInput, onSuccess: (response: IGetPlayerScoreResponse) => void, onError?: (response: IResponse | string | IGooglePlayErrorResponse) => void): void;

    /**
     * Launches the native Play Games leaderboard view controller to show all the leaderboards.
     */
    showAllLeaderboards(onSuccess?: (response: IResponse) => void, onError?: (response: IResponse | string | IGooglePlayErrorResponse) => void): void;

    /**
     * Launches directly into the specified leaderboard:
     */
    showLeaderboard(data: IShowLeaderboardInput, onSuccess?: (response: IResponse) => void, onError?: (response: IResponse | string | IGooglePlayErrorResponse) => void): void;

    /**
     * Unlocks the specified achievement:
     */
    unlockAchievement(data: IUnlockAchievementInput, onSuccess?: (response: IResponse) => void, onError?: (response: IResponse | string | IGooglePlayErrorResponse) => void): void;

    /**
     * Unlocks the specified achievement and waits for response
     */
    unlockAchievementNow(data: IUnlockAchievementInput, onSuccess: (response: IUnlockAchievementResponse) => void, onError?: (response: IResponse | string | IGooglePlayErrorResponse) => void): void;

    /**
     * Increments the specified incremental achievement by the provided numSteps:
     */
    incrementAchievement(data: IIncrementAchievementInput, onSuccess?: (response: IResponse) => void, onError?: (response: IResponse | string | IGooglePlayErrorResponse) => void): void;

    /**
     * Increments the specified incremental achievement by the provided numSteps and waits for response
     */
    incrementAchievementNow(data: IIncrementAchievementInput, onSuccess?: () => void, onError?: (response: IResponse | string | IGooglePlayErrorResponse) => void): void;

    /**
     * Launches the native Play Games achievements view controller to show the user’s achievements.
     */
    showAchievements(onSuccess?: (response: IResponse) => void, onError?: (response: IResponse | string | IGooglePlayErrorResponse) => void): void;

    /**
     * Save game to Play Games
     */
    saveGame(data: ISaveGameInput, onSuccess?: (response: ISaveGameResponse) => void, onError?: (response: ILoadGameResponse | ISnapshotConflictResponse | IResponse | string | IGooglePlayErrorResponse) => void): void;

    /**
     * Load saved game from Play Games
     */
    loadGame(data: ILoadGameInput, onSuccess?: (response: ILoadGameResponse) => void, onError?: (response: ISnapshotConflictResponse | IResponse | string | IGooglePlayErrorResponse) => void): void;
    
    /**
     * resolve save conflict from Play Games
     */
    resolveSnapshotConflict(data: IResolveSnapshotConflictInput, onSuccess?: (response: IResponse) => void, onError?: (response: ISnapshotConflictResponse | IResponse | string | IGooglePlayErrorResponse) => void): void;
    
    /**
     * Delete saved game from Play Games
     */
    deleteSaveGame(data: IDeleteGameInput, onSuccess?: (response: IResponse) => void, onError?: (response: IResponse | string | IGooglePlayErrorResponse) => void): void;

    OK: number;
    NOT_SIGN_IN: number;
    LOAD_GAME_ERROR_NOT_EXIST: number;
    SAVE_GAME_ERROR_WRONG_PREVIOUS_SAVE: number;
    ERROR_SNAPSHOT_CONFLICT: number;
    ERROR_HAVE_NOT_SNAPSHOT_CONFLICT: number;
    GOOGLE_PLAY_ERROR: number;
    UNKNOWN_ERROR: number;

    RESOLUTION_POLICY_MANUAL: number;
    RESOLUTION_POLICY_LONGEST_PLAYTIME: number;
    RESOLUTION_POLICY_LAST_KNOWN_GOOD: number;
    RESOLUTION_POLICY_MOST_RECENTLY_MODIFIED: number;
    RESOLUTION_POLICY_HIGHEST_PROGRESS: number;
}

interface Cordova {
    plugins: {
        playGamesServices: IPlayGamesServices;
    }
}

interface Window {
    plugins: {
        playGamesServices: IPlayGamesServices;
    }
}
