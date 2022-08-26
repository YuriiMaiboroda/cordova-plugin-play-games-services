/*
       Licensed to the Apache Software Foundation (ASF) under one
       or more contributor license agreements.  See the NOTICE file
       distributed with this work for additional information
       regarding copyright ownership.  The ASF licenses this file
       to you under the Apache License, Version 2.0 (the
       "License"); you may not use this file except in compliance
       with the License.  You may obtain a copy of the License at

         http://www.apache.org/licenses/LICENSE-2.0

       Unless required by applicable law or agreed to in writing,
       software distributed under the License is distributed on an
       "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
       KIND, either express or implied.  See the License for the
       specific language governing permissions and limitations
       under the License.
 */

package com.berriart.cordova.plugins;

import android.app.Activity;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.util.Log;

import androidx.annotation.NonNull;

import com.berriart.cordova.plugins.GameHelper.GameHelperListener;
import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.GoogleApiAvailability;
import com.google.android.gms.common.api.ApiException;
import com.google.android.gms.common.api.Status;
import com.google.android.gms.games.AnnotatedData;
import com.google.android.gms.games.Games;
import com.google.android.gms.games.GamesClientStatusCodes;
import com.google.android.gms.games.GamesStatusCodes;
import com.google.android.gms.games.Player;
import com.google.android.gms.games.SnapshotsClient;
import com.google.android.gms.games.leaderboard.LeaderboardScore;
import com.google.android.gms.games.leaderboard.LeaderboardVariant;
import com.google.android.gms.games.leaderboard.ScoreSubmissionData;
import com.google.android.gms.games.snapshot.Snapshot;
import com.google.android.gms.games.snapshot.SnapshotContents;
import com.google.android.gms.games.snapshot.SnapshotMetadata;
import com.google.android.gms.games.snapshot.SnapshotMetadataChange;
import com.google.android.gms.games.snapshot.Snapshots;
import com.google.android.gms.tasks.Continuation;
import com.google.android.gms.tasks.OnCompleteListener;
import com.google.android.gms.tasks.Task;

import org.apache.cordova.CallbackContext;
import org.apache.cordova.CordovaInterface;
import org.apache.cordova.CordovaPlugin;
import org.apache.cordova.CordovaWebView;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

public class PlayGamesServices extends CordovaPlugin implements GameHelperListener {

    private static final String LOGTAG = "CordovaPlayGamesService";

    private static final String ACTION_AUTH = "auth";
    private static final String ACTION_SIGN_OUT = "signOut";
    private static final String ACTION_IS_SIGNEDIN = "isSignedIn";

    private static final String ACTION_SUBMIT_SCORE = "submitScore";
    private static final String ACTION_SUBMIT_SCORE_NOW = "submitScoreNow";
    private static final String ACTION_GET_PLAYER_SCORE = "getPlayerScore";
    private static final String ACTION_SHOW_ALL_LEADERBOARDS = "showAllLeaderboards";
    private static final String ACTION_SHOW_LEADERBOARD = "showLeaderboard";

    private static final String ACTION_UNLOCK_ACHIEVEMENT = "unlockAchievement";
    private static final String ACTION_UNLOCK_ACHIEVEMENT_NOW = "unlockAchievementNow";
    private static final String ACTION_INCREMENT_ACHIEVEMENT = "incrementAchievement";
    private static final String ACTION_INCREMENT_ACHIEVEMENT_NOW = "incrementAchievementNow";
    private static final String ACTION_SHOW_ACHIEVEMENTS = "showAchievements";
    private static final String ACTION_SHOW_PLAYER = "showPlayer";

    private static final String ACTION_SAVE_GAME = "saveGame";
    private static final String ACTION_RESOLVE_SNAPSHOT_CONFLICT = "resolveSnapshotConflict";
    private static final String ACTION_LOAD_GAME = "loadGame";
    private static final String ACTION_DELETE_SAVE_GAME = "deleteSaveGame";

    private static final int ACTIVITY_CODE_SHOW_LEADERBOARD = 0;
    private static final int ACTIVITY_CODE_SHOW_ACHIEVEMENTS = 1;

    private static final int LOAD_GAME_ERROR_FAILED = 0;
    private static final int LOAD_GAME_ERROR_NOT_EXIST = 1;
    private static final int LOAD_GAME_ERROR_NOT_SIGNED = 2;

    private static final int ERROR_SNAPSHOT_CONFLICT = 3;

    private static final int SAVE_GAME_ERROR_WRONG_PREVIOUSE_SAVE = 4;

    private GameHelper gameHelper;

    private CallbackContext authCallbackContext;
    private int googlePlayServicesReturnCode;

    private SaveConflictData saveConflictData;

    @Override
    public void initialize(CordovaInterface cordova, CordovaWebView webView) {
        super.initialize(cordova, webView);
        Activity cordovaActivity = cordova.getActivity();

        googlePlayServicesReturnCode = GoogleApiAvailability.getInstance().isGooglePlayServicesAvailable(cordovaActivity);

        if (googlePlayServicesReturnCode == ConnectionResult.SUCCESS) {
            gameHelper = new GameHelper(cordovaActivity, GameHelper.CLIENT_GAMES | GameHelper.CLIENT_SNAPSHOT);
            if ((cordova.getContext().getApplicationInfo().flags & ApplicationInfo.FLAG_DEBUGGABLE) != 0) {
                gameHelper.enableDebugLog(true);
            }
            gameHelper.setup(this);
        } else {
            Log.w(LOGTAG, String.format("GooglePlayServices not available. Error: '" +
                    GoogleApiAvailability.getInstance().getErrorString(googlePlayServicesReturnCode) +
                    "'. Error Code: " + googlePlayServicesReturnCode));
        }

        cordova.setActivityResultCallback(this);
    }

    @Override
    public void onStart() {
        super.onStart();
        gameHelper.onStart(cordova.getActivity());
    }

    @Override
    public boolean execute(String action, JSONArray inputs, CallbackContext callbackContext) throws JSONException {

        JSONObject options = inputs.optJSONObject(0);

        if (gameHelper == null) {
            Log.w(LOGTAG, String.format("Tried calling: '" + action + "', but error with GooglePlayServices"));
            Log.w(LOGTAG, String.format("GooglePlayServices not available. Error: '" +
                    GoogleApiAvailability.getInstance().getErrorString(googlePlayServicesReturnCode) +
                    "'. Error Code: " + googlePlayServicesReturnCode));

            JSONObject googlePlayError = new JSONObject();
            googlePlayError.put("errorCode", googlePlayServicesReturnCode);
            googlePlayError.put("errorString", GoogleApiAvailability.getInstance().getErrorString(googlePlayServicesReturnCode));

            JSONObject result = new JSONObject();
            result.put("googlePlayError", googlePlayError);
            callbackContext.error(result);

            return true;
        }

        Log.i(LOGTAG, String.format("Processing action " + action + " ..."));

        if (ACTION_AUTH.equals(action)) {
            executeAuth(options, callbackContext);
        } else if (ACTION_SIGN_OUT.equals(action)) {
            executeSignOut(callbackContext);
        } else if (ACTION_IS_SIGNEDIN.equals(action)) {
            executeIsSignedIn(callbackContext);
        } else if (ACTION_SUBMIT_SCORE.equals(action)) {
            executeSubmitScore(options, callbackContext);
        } else if (ACTION_SUBMIT_SCORE_NOW.equals(action)) {
            executeSubmitScoreNow(options, callbackContext);
        } else if (ACTION_GET_PLAYER_SCORE.equals(action)) {
            executeGetPlayerScore(options, callbackContext);
        } else if (ACTION_SHOW_ALL_LEADERBOARDS.equals(action)) {
            executeShowAllLeaderboards(callbackContext);
        } else if (ACTION_SHOW_LEADERBOARD.equals(action)) {
            executeShowLeaderboard(options, callbackContext);
        } else if (ACTION_SHOW_ACHIEVEMENTS.equals(action)) {
            executeShowAchievements(callbackContext);
        } else if (ACTION_UNLOCK_ACHIEVEMENT.equals(action)) {
            executeUnlockAchievement(options, callbackContext);
        } else if (ACTION_UNLOCK_ACHIEVEMENT_NOW.equals(action)) {
            executeUnlockAchievementNow(options, callbackContext);
        } else if (ACTION_INCREMENT_ACHIEVEMENT.equals(action)) {
            executeIncrementAchievement(options, callbackContext);
        } else if (ACTION_INCREMENT_ACHIEVEMENT_NOW.equals(action)) {
            executeIncrementAchievementNow(options, callbackContext);
        } else if (ACTION_SHOW_PLAYER.equals(action)) {
            executeShowPlayer(callbackContext);
        } else if (ACTION_SAVE_GAME.equals(action)) {
            executeSaveGame(options, callbackContext);
        } else if (ACTION_RESOLVE_SNAPSHOT_CONFLICT.equals(action)) {
            executeResolveSnapshotConflict(options, callbackContext);
        } else if (ACTION_LOAD_GAME.equals(action)) {
            executeLoadGame(options, callbackContext);
        } else if (ACTION_DELETE_SAVE_GAME.equals(action)) {
            executeDeleteSaveGame(options, callbackContext);
        } else {
            return false; // Tried to execute an unknown method
        }

        return true;
    }

    private void executeAuth(final JSONObject options, final CallbackContext callbackContext) {

        authCallbackContext = callbackContext;
        Boolean silent = options.optBoolean("silent");

        Log.d(LOGTAG, "executeAuth" + (silent ? " silent" : ""));
        cordova.getActivity().runOnUiThread(new Runnable() {
            @Override
            public void run() {
                if (silent) {
                    gameHelper.silentSignIn();
                } else {
                    gameHelper.beginUserInitiatedSignIn();
                }
            }
        });
    }

    private void executeSignOut(final CallbackContext callbackContext) {
        Log.d(LOGTAG, "executeSignOut");

        cordova.getActivity().runOnUiThread(new Runnable() {
            @Override
            public void run() {
                gameHelper.signOut();
                callbackContext.success();
            }
        });
    }

    private void executeIsSignedIn(final CallbackContext callbackContext) {
        Log.d(LOGTAG, "executeIsSignedIn");

        cordova.getActivity().runOnUiThread(new Runnable() {
            @Override
            public void run() {
                try {
                    JSONObject result = new JSONObject();
                    result.put("isSignedIn", gameHelper.isSignedIn());
                    callbackContext.success(result);
                } catch (JSONException e) {
                    Log.w(LOGTAG, "executeIsSignedIn: unable to determine if user is signed in or not", e);
                    callbackContext.error("executeIsSignedIn: unable to determine if user is signed in or not");
                }
            }
        });
    }

    private void executeSubmitScore(final JSONObject options, final CallbackContext callbackContext) throws JSONException {
        Log.d(LOGTAG, "executeSubmitScore");

        cordova.getActivity().runOnUiThread(new Runnable() {
            @Override
            public void run() {
                try {
                    if (gameHelper.isSignedIn()) {
                        Games.getLeaderboardsClient(cordova.getActivity(), gameHelper.getGoogleAccount())
                            .submitScore(options.getString("leaderboardId"), options.getLong("score"));
                        callbackContext.success("executeSubmitScore: score submited successfully");
                    } else {
                        Log.w(LOGTAG, "executeSubmitScore: not yet signed in");
                        callbackContext.error("executeSubmitScore: not yet signed in");
                    }
                } catch (JSONException e) {
                    Log.w(LOGTAG, "executeSubmitScore: unexpected error", e);
                    callbackContext.error("executeSubmitScore: error while submitting score");
                }
            }
        });
    }

    private void executeSubmitScoreNow(final JSONObject options, final CallbackContext callbackContext) throws JSONException {
        Log.d(LOGTAG, "executeSubmitScoreNow");

        cordova.getActivity().runOnUiThread(new Runnable() {
            @Override
            public void run() {
                try {
                    if (gameHelper.isSignedIn()) {
                        Games.getLeaderboardsClient(cordova.getActivity(), gameHelper.getGoogleAccount())
                            .submitScoreImmediate(options.getString("leaderboardId"), options.getLong("score"))
                            .addOnCompleteListener(new OnCompleteListener<ScoreSubmissionData>() {
                                @Override
                                public void onComplete(@NonNull Task<ScoreSubmissionData> task) {
                                    if (task.isSuccessful()) {
                                        ScoreSubmissionData scoreSubmissionData = task.getResult();
                                        if (scoreSubmissionData != null) {
                                            try {
                                                ScoreSubmissionData.Result scoreResult = scoreSubmissionData.getScoreResult(LeaderboardVariant.TIME_SPAN_ALL_TIME);
                                                JSONObject result = new JSONObject();
                                                result.put("leaderboardId", scoreSubmissionData.getLeaderboardId());
                                                result.put("playerId", scoreSubmissionData.getPlayerId());
                                                result.put("formattedScore", scoreResult.formattedScore);
                                                result.put("newBest", scoreResult.newBest);
                                                result.put("rawScore", scoreResult.rawScore);
                                                result.put("scoreTag", scoreResult.scoreTag);
                                                callbackContext.success(result);
                                            } catch (JSONException e) {
                                                Log.w(LOGTAG, "executeSubmitScoreNow: unexpected error", e);
                                                callbackContext.error("executeSubmitScoreNow: error while submitting score");
                                            }
                                        } else {
                                            Log.w(LOGTAG, "executeSubmitScoreNow: can't submit the score");
                                            callbackContext.error("executeSubmitScoreNow: can't submit the score");
                                        }
                                    } else {
                                        Exception exception = task.getException();
                                        if (exception instanceof ApiException) {
                                            ApiException apiException = (ApiException) exception;
                                            final Status status = apiException.getStatus();
                                            Log.w(LOGTAG, "executeSubmitScoreNow error: " + status.getStatusMessage() + "(" + status.getStatusCode() + ")");
                                            callbackContext.error("executeSubmitScoreNow error: " + status.getStatusMessage() + "(" + status.getStatusCode() + ")");
                                        } else {
                                            Log.w(LOGTAG, "executeSubmitScoreNow error: " + exception.getMessage(), exception);
                                            callbackContext.error("executeSubmitScoreNow error: " + exception.getMessage());
                                        }
                                    }
                                }
                            });
                    } else {
                        Log.w(LOGTAG, "executeSubmitScoreNow: not yet signed in");
                        callbackContext.error("executeSubmitScoreNow: not yet signed in");
                    }
                } catch (JSONException e) {
                    Log.w(LOGTAG, "executeSubmitScoreNow: unexpected error", e);
                    callbackContext.error("executeSubmitScoreNow: error while submitting score");
                }
            }
        });
    }

    private void executeGetPlayerScore(final JSONObject options, final CallbackContext callbackContext) throws JSONException {
        Log.d(LOGTAG, "executeGetPlayerScore");

        cordova.getActivity().runOnUiThread(new Runnable() {
            @Override
            public void run() {
                try {
                    if (gameHelper.isSignedIn()) {
                        Games.getLeaderboardsClient(cordova.getActivity(), gameHelper.getGoogleAccount())
                            .loadCurrentPlayerLeaderboardScore(options.getString("leaderboardId"), LeaderboardVariant.TIME_SPAN_ALL_TIME, LeaderboardVariant.COLLECTION_PUBLIC)
                            .addOnCompleteListener(new OnCompleteListener<AnnotatedData<LeaderboardScore>>() {
                                @Override
                                public void onComplete(@NonNull Task<AnnotatedData<LeaderboardScore>> task) {
                                    if (task.isSuccessful()) {
                                        AnnotatedData<LeaderboardScore> scoreAnnotatedData = task.getResult();
                                        LeaderboardScore score = scoreAnnotatedData.get();
                                        if (score != null) {
                                            try {
                                                JSONObject result = new JSONObject();
                                                result.put("playerScore", score.getRawScore());
                                                result.put("playerRank", score.getRank());
                                                result.put("isStale", scoreAnnotatedData.isStale());
                                                callbackContext.success(result);
                                            } catch (JSONException e) {
                                                Log.w(LOGTAG, "executeGetPlayerScore: unexpected error", e);
                                                callbackContext.error("executeGetPlayerScore: error while retrieving score");
                                            }
                                        } else {
                                            Log.w(LOGTAG, "There isn't have any score record for this player");
                                            callbackContext.error("There isn't have any score record for this player");
                                        }
                                    } else {
                                        Exception exception = task.getException();
                                        if (exception instanceof ApiException) {
                                            ApiException apiException = (ApiException) exception;
                                            final Status status = apiException.getStatus();
                                            Log.w(LOGTAG, "executeGetPlayerScore error: " + status.getStatusMessage() + "(" + status.getStatusCode() + ")");
                                            callbackContext.error("executeGetPlayerScore error: " + status.getStatusMessage() + "(" + status.getStatusCode() + ")");
                                        } else {
                                            Log.w(LOGTAG, "executeGetPlayerScore error: " + exception.getMessage(), exception);
                                            callbackContext.error("executeGetPlayerScore error: " + exception.getMessage());
                                        }
                                    }
                                }
                            });
                    } else {
                        Log.w(LOGTAG, "executeGetPlayerScore: not yet signed in");
                        callbackContext.error("executeGetPlayerScore: not yet signed in");
                    }
                } catch (JSONException e) {
                    Log.w(LOGTAG, "executeGetPlayerScore: unexpected error", e);
                    callbackContext.error("executeGetPlayerScore: error while retrieving score");
                }
            }
        });
    }

    private void executeShowAllLeaderboards(final CallbackContext callbackContext) {
        Log.d(LOGTAG, "executeShowAllLeaderboards");

        final PlayGamesServices plugin = this;

        cordova.getActivity().runOnUiThread(new Runnable() {
            @Override
            public void run() {
                try {
                    if (gameHelper.isSignedIn()) {
                        Games.getLeaderboardsClient(cordova.getActivity(), gameHelper.getGoogleAccount())
                            .getAllLeaderboardsIntent()
                            .addOnCompleteListener(new OnCompleteListener<Intent>() {
                                @Override
                                public void onComplete(@NonNull Task<Intent> task) {
                                    if (task.isSuccessful()) {
                                        Intent allLeaderboardsIntent = task.getResult();
                                        cordova.startActivityForResult(plugin, allLeaderboardsIntent, ACTIVITY_CODE_SHOW_LEADERBOARD);
                                        callbackContext.success();
                                    } else {
                                        Exception e = task.getException();
                                        if (e instanceof ApiException) {
                                            ApiException apiException = (ApiException) e;
                                            final Status status = apiException.getStatus();
                                            Log.w(LOGTAG, "executeShowAllLeaderboards error: " + status.getStatusMessage() + "(" + status.getStatusCode() + ")");
                                            callbackContext.error("executeShowAllLeaderboards error: " + status.getStatusMessage() + "(" + status.getStatusCode() + ")");
                                        } else {
                                            Log.w(LOGTAG, "executeShowAllLeaderboards error: " + e.getMessage(), e);
                                            callbackContext.error("executeShowAllLeaderboards error: " + e.getMessage());
                                        }
                                    }
                                }
                            });
                    } else {
                        Log.w(LOGTAG, "executeShowAllLeaderboards: not yet signed in");
                        callbackContext.error("executeShowAllLeaderboards: not yet signed in");
                    }
                } catch (Exception e) {
                    Log.w(LOGTAG, "executeShowAllLeaderboards: unexpected error", e);
                    callbackContext.error("executeShowAllLeaderboards: error while showing leaderboards");
                }
            }
        });
    }

    private void executeShowLeaderboard(final JSONObject options, final CallbackContext callbackContext) {
        Log.d(LOGTAG, "executeShowLeaderboard");

        final PlayGamesServices plugin = this;

        cordova.getActivity().runOnUiThread(new Runnable() {
            @Override
            public void run() {
                try {
                    if (gameHelper.isSignedIn()) {
                        Games.getLeaderboardsClient(cordova.getActivity(), gameHelper.getGoogleAccount())
                            .getLeaderboardIntent(options.getString("leaderboardId"))
                            .addOnCompleteListener(new OnCompleteListener<Intent>() {
                                @Override
                                public void onComplete(@NonNull Task<Intent> task) {
                                    if (task.isSuccessful()) {
                                        Intent leaderboardIntent = task.getResult();
                                        cordova.startActivityForResult(plugin, leaderboardIntent, ACTIVITY_CODE_SHOW_LEADERBOARD);
                                        callbackContext.success();
                                    } else {
                                        Exception e = task.getException();
                                        if (e instanceof ApiException) {
                                            ApiException apiException = (ApiException) e;
                                            final Status status = apiException.getStatus();
                                            Log.w(LOGTAG, "executeShowLeaderboard error: " + status.getStatusMessage() + "(" + status.getStatusCode() + ")");
                                            callbackContext.error("executeShowLeaderboard error: " + status.getStatusMessage() + "(" + status.getStatusCode() + ")");
                                        } else {
                                            Log.w(LOGTAG, "executeShowLeaderboard error: " + e.getMessage(), e);
                                            callbackContext.error("executeShowLeaderboard error: " + e.getMessage());
                                        }
                                    }
                                }
                            });
                    } else {
                        Log.w(LOGTAG, "executeShowLeaderboard: not yet signed in");
                        callbackContext.error("executeShowLeaderboard: not yet signed in");
                    }
                } catch (JSONException e) {
                    Log.w(LOGTAG, "executeShowLeaderboard: unexpected error", e);
                    callbackContext.error("executeShowLeaderboard: error while showing specific leaderboard");
                }
            }
        });
    }

    private void executeUnlockAchievement(final JSONObject options, final CallbackContext callbackContext) {
        Log.d(LOGTAG, "executeUnlockAchievement");

        cordova.getActivity().runOnUiThread(new Runnable() {
            @Override
            public void run() {
                try {
                    if (gameHelper.isSignedIn()) {
                        Games.getAchievementsClient(cordova.getActivity(), gameHelper.getGoogleAccount()).unlock(options.optString("achievementId"));
                        callbackContext.success();
                    } else {
                        Log.w(LOGTAG, "executeUnlockAchievement: not yet signed in");
                        callbackContext.error("executeUnlockAchievement: not yet signed in");
                    }
                } catch (Exception e) {
                    Log.w(LOGTAG, "executeUnlockAchievement: unexpected error", e);
                    callbackContext.error("executeUnlockAchievement: error while unlocking achievement");
                }
            }
        });
    }

    private void executeUnlockAchievementNow(final JSONObject options, final CallbackContext callbackContext) {
        Log.d(LOGTAG, "executeUnlockAchievementNow");

        cordova.getActivity().runOnUiThread(new Runnable() {
            @Override
            public void run() {

                if (gameHelper.isSignedIn()) {
                    String achievementId = options.optString("achievementId");
                    Games.getAchievementsClient(cordova.getActivity(), gameHelper.getGoogleAccount())
                        .unlockImmediate(achievementId)
                        .addOnCompleteListener(new OnCompleteListener<Void>() {
                            @Override
                            public void onComplete(@NonNull Task<Void> task) {
                                if (task.isSuccessful()) {
                                    try {
                                        JSONObject result = new JSONObject();
                                        result.put("achievementId", achievementId);
                                        callbackContext.success(result);
                                    } catch (JSONException e) {
                                        Log.w(LOGTAG, "executeUnlockAchievementNow: unexpected error", e);
                                        callbackContext.error("executeUnlockAchievementNow: error while unlocking achievement");
                                    }
                                } else {
                                    Exception e = task.getException();
                                    if (e instanceof ApiException) {
                                        ApiException apiException = (ApiException) e;
                                        final Status status = apiException.getStatus();
                                        Log.w(LOGTAG, "executeUnlockAchievementNow error: " + status.getStatusMessage() + "(" + status.getStatusCode() + ")");
                                        callbackContext.error("executeUnlockAchievementNow error: " + status.getStatusMessage() + "(" + status.getStatusCode() + ")");
                                    } else {
                                        Log.w(LOGTAG, "executeUnlockAchievementNow error: " + e.getMessage(), e);
                                        callbackContext.error("executeUnlockAchievementNow error: " + e.getMessage());
                                    }
                                }
                            }
                        });
                } else {
                    Log.w(LOGTAG, "executeUnlockAchievementNow: not yet signed in");
                    callbackContext.error("executeUnlockAchievementNow: not yet signed in");
                }
            }
        });
    }

    private void executeIncrementAchievement(final JSONObject options, final CallbackContext callbackContext) {
        Log.d(LOGTAG, "executeIncrementAchievement");

        cordova.getActivity().runOnUiThread(new Runnable() {
            @Override
            public void run() {
                if (gameHelper.isSignedIn()) {
                    Games.getAchievementsClient(cordova.getActivity(), gameHelper.getGoogleAccount()).increment(options.optString("achievementId"), options.optInt("numSteps"));
                    callbackContext.success();
                } else {
                    Log.w(LOGTAG, "executeIncrementAchievement: not yet signed in");
                    callbackContext.error("executeIncrementAchievement: not yet signed in");
                }
            }
        });
    }

    private void executeIncrementAchievementNow(final JSONObject options, final CallbackContext callbackContext) {
        Log.d(LOGTAG, "executeIncrementAchievementNow");

        cordova.getActivity().runOnUiThread(new Runnable() {
            @Override
            public void run() {
                if (gameHelper.isSignedIn()) {
                    String achievementId = options.optString("achievementId");
                    Games.getAchievementsClient(cordova.getActivity(), gameHelper.getGoogleAccount())
                        .incrementImmediate(options.optString("achievementId"), options.optInt("numSteps"))
                        .addOnCompleteListener(new OnCompleteListener<Boolean>() {
                            @Override
                            public void onComplete(@NonNull Task<Boolean> task) {
                                if (task.isSuccessful()) {
                                    try {
                                        Boolean unlocked = task.getResult();
                                        JSONObject result = new JSONObject();
                                        result.put("achievementId", achievementId);
                                        result.put("isUnlocked", unlocked);
                                        callbackContext.success(result);
                                    } catch (JSONException e) {
                                        Log.w(LOGTAG, "executeIncrementAchievementNow: unexpected error", e);
                                        callbackContext.error("executeIncrementAchievementNow: error while incrementing achievement");
                                    }
                                } else {
                                    Exception e = task.getException();
                                    if (e instanceof ApiException) {
                                        ApiException apiException = (ApiException) e;
                                        final Status status = apiException.getStatus();
                                        Log.w(LOGTAG, "executeIncrementAchievementNow error: " + status.getStatusMessage() + "(" + status.getStatusCode() + ")");
                                        callbackContext.error("executeIncrementAchievementNow error: " + status.getStatusMessage() + "(" + status.getStatusCode() + ")");
                                    } else {
                                        Log.w(LOGTAG, "executeIncrementAchievementNow error: " + e.getMessage(), e);
                                        callbackContext.error("executeIncrementAchievementNow error: " + e.getMessage());
                                    }
                                }
                            }
                        });
                } else {
                    Log.w(LOGTAG, "executeIncrementAchievement: not yet signed in");
                    callbackContext.error("executeIncrementAchievement: not yet signed in");
                }
            }
        });
    }

    private void executeShowAchievements(final CallbackContext callbackContext) {
        Log.d(LOGTAG, "executeShowAchievements");

        final PlayGamesServices plugin = this;

        cordova.getActivity().runOnUiThread(new Runnable() {
            @Override
            public void run() {
                try {
                    if (gameHelper.isSignedIn()) {
                        Games.getAchievementsClient(cordova.getActivity(), gameHelper.getGoogleAccount())
                            .getAchievementsIntent()
                            .addOnCompleteListener(new OnCompleteListener<Intent>() {
                                @Override
                                public void onComplete(@NonNull Task<Intent> task) {
                                    if (task.isSuccessful()) {
                                        Intent achievementsIntent = task.getResult();
                                        cordova.startActivityForResult(plugin, achievementsIntent, ACTIVITY_CODE_SHOW_ACHIEVEMENTS);
                                        callbackContext.success();
                                    } else {
                                        Exception e = task.getException();
                                        if (e instanceof ApiException) {
                                            ApiException apiException = (ApiException) e;
                                            final Status status = apiException.getStatus();
                                            Log.w(LOGTAG, "executeShowAchievements error: " + status.getStatusMessage() + "(" + status.getStatusCode() + ")");
                                            callbackContext.error("executeShowAchievements error: " + status.getStatusMessage() + "(" + status.getStatusCode() + ")");
                                        } else {
                                            Log.w(LOGTAG, "executeShowAchievements error: " + e.getMessage(), e);
                                            callbackContext.error("executeShowAchievements error: " + e.getMessage());
                                        }
                                    }
                                }
                            });
                    } else {
                        Log.w(LOGTAG, "executeShowAchievements: not yet signed in");
                        callbackContext.error("executeShowAchievements: not yet signed in");
                    }
                } catch (Exception e) {
                    Log.w(LOGTAG, "executeShowAchievements: unexpected error", e);
                    callbackContext.error("executeShowAchievements: error while showing achievements");
                }
            }
        });
    }

    private void executeShowPlayer(final CallbackContext callbackContext) {
        Log.d(LOGTAG, "executeShowPlayer");
        cordova.getActivity().runOnUiThread(new Runnable() {
            @Override
            public void run() {
                try {
                    if (gameHelper.isSignedIn()) {
                        Games.getPlayersClient(cordova.getActivity(), gameHelper.getGoogleAccount())
                            .getCurrentPlayer()
                            .addOnCompleteListener(new OnCompleteListener<Player>() {
                                @Override
                                public void onComplete(@NonNull Task<Player> task) {
                                    if (task.isSuccessful()) {
                                        Player player = task.getResult();
                                        try {
                                            JSONObject playerJson = new JSONObject();
                                            playerJson.put("displayName", player.getDisplayName());
                                            playerJson.put("playerId", player.getPlayerId());
                                            playerJson.put("title", player.getTitle());
                                            playerJson.put("iconImageUrl", player.getIconImageUrl());
                                            playerJson.put("hiResIconImageUrl", player.getHiResImageUrl());
                                            callbackContext.success(playerJson);
                                        } catch (JSONException e) {
                                            Log.w(LOGTAG, "executeShowPlayer error: " + e.getMessage(), e);
                                            callbackContext.error("executeShowPlayer error: " + e.getMessage());
                                        }
                                    } else {
                                        Exception e = task.getException();
                                        if (e instanceof ApiException) {
                                            ApiException apiException = (ApiException) e;
                                            final Status status = apiException.getStatus();
                                            Log.w(LOGTAG, "executeShowPlayer error: " + status.getStatusMessage() + "(" + status.getStatusCode() + ")");
                                            callbackContext.error("executeShowPlayer error: " + status.getStatusMessage() + "(" + status.getStatusCode() + ")");
                                        } else {
                                            Log.w(LOGTAG, "executeShowPlayer error: " + e.getMessage(), e);
                                            callbackContext.error("executeShowPlayer error: " + e.getMessage());
                                        }
                                    }
                                }
                            });
                    } else {
                        Log.w(LOGTAG, "executeShowPlayer: not yet signed in");
                        callbackContext.error("executeShowPlayer: not yet signed in");
                    }
                }
                catch(Exception e) {
                    Log.w(LOGTAG, "executeShowPlayer: Error providing player data", e);
                    callbackContext.error("executeShowPlayer: Error providing player data");
                }
            }
        });
    }

    private void executeSaveGame(final JSONObject options, final CallbackContext callbackContext) {
        Log.d(LOGTAG, "executeSaveGame");
        cordova.getActivity().runOnUiThread(new Runnable() {
            @Override
            public void run() {
                try {
                    if (gameHelper.isSignedIn()) {
                        String saveName = options.getString("saveName");
                        String saveData = options.getString("saveData");
                        Long previousSaveTime = options.optLong("previousSaveTime", -1);
                        Games.getSnapshotsClient(cordova.getActivity(), gameHelper.getGoogleAccount())
                            .open(saveName, true, Snapshots.RESOLUTION_POLICY_MANUAL)
                            .addOnCompleteListener(new OnCompleteListener<SnapshotsClient.DataOrConflict<Snapshot>>() {
                                @Override
                                public void onComplete(@NonNull Task<SnapshotsClient.DataOrConflict<Snapshot>> task) {
                                    if (task.isSuccessful()) {
                                        SnapshotsClient.DataOrConflict<Snapshot> dataOrConflict = task.getResult();
                                        if (!dataOrConflict.isConflict()) {
                                            Snapshot snapshot = dataOrConflict.getData();
                                            if (snapshot != null && snapshot.getSnapshotContents() != null) {
                                                SnapshotContents snapshotContents = snapshot.getSnapshotContents();
                                                byte[] data = null;
                                                try {
                                                    data = snapshotContents.readFully();
                                                } catch (IOException e) {}
                                                if (previousSaveTime == -1 || data == null || data.length == 0 ||  previousSaveTime == snapshot.getMetadata().getLastModifiedTimestamp()) {
                                                    snapshotContents.writeBytes(saveData.getBytes(StandardCharsets.UTF_8));
                                                    Games.getSnapshotsClient(cordova.getActivity(), gameHelper.getGoogleAccount())
                                                        .commitAndClose(snapshot, SnapshotMetadataChange.EMPTY_CHANGE)
                                                        .addOnCompleteListener(new OnCompleteListener<SnapshotMetadata>() {
                                                            @Override
                                                            public void onComplete(@NonNull Task<SnapshotMetadata> task) {
                                                                try {
                                                                    if (task.isSuccessful()) {
                                                                        Long saveTime = task.getResult().getLastModifiedTimestamp();
                                                                        JSONObject playerJson = new JSONObject();
                                                                        playerJson.put("saveTime", saveTime);
                                                                        callbackContext.success(playerJson);
                                                                    } else {
                                                                        task.getResult(ApiException.class);
                                                                    }
                                                                } catch (Exception e) {
                                                                    if (e instanceof ApiException) {
                                                                        ApiException apiException = (ApiException) e;
                                                                        final Status status = apiException.getStatus();
                                                                        Log.w(LOGTAG, "executeSaveGame error: " + status.getStatusMessage() + "(" + status.getStatusCode() + ")");
                                                                        callbackContext.error("executeSaveGame error: " + status.getStatusMessage() + "(" + status.getStatusCode() + ")");
                                                                    } else {
                                                                        Log.w(LOGTAG, "executeSaveGame error: " + e.getMessage(), e);
                                                                        callbackContext.error("executeSaveGame error: " + e.getMessage());
                                                                    }
                                                                }
                                                            }
                                                        });
                                                } else {
                                                    Log.w(LOGTAG, "executeSaveGame: wrong previouse save");
                                                    JSONObject result = new JSONObject();
                                                    try {
                                                        byte[] snapshotData = snapshotContents.readFully();
                                                        String saveData = (snapshotData == null || snapshotData.length == 0) ? "" : new String(snapshotData, StandardCharsets.UTF_8);
                                                        Long saveTime = snapshot.getMetadata().getLastModifiedTimestamp();

                                                        result.put("status", SAVE_GAME_ERROR_WRONG_PREVIOUSE_SAVE);
                                                        result.put("saveData", saveData);
                                                        result.put("saveTime", saveTime);
                                                    } catch (Exception e) {
                                                        Log.w(LOGTAG, "executeSaveGame: error on create conflict data", e);
                                                    }
                                                    callbackContext.error(result);
                                                }
                                            } else {
                                                Log.w(LOGTAG, "executeSaveGame: snapshot or snapshotContents is null");
                                                callbackContext.error("executeSaveGame: snapshot or snapshotContents is null");
                                            }
                                        } else {
                                            Log.w(LOGTAG, "executeSaveGame: Conflict on open");
                                            JSONObject result = new JSONObject();
                                            try {
                                                SnapshotsClient.SnapshotConflict snapshotConflict = dataOrConflict.getConflict();
                                                saveConflictData = new SaveConflictData();
                                                saveConflictData.conflictId = snapshotConflict.getConflictId();
                                                saveConflictData.serverSnapshot = snapshotConflict.getSnapshot();
                                                saveConflictData.localSnapshot = snapshotConflict.getConflictingSnapshot();
                                                byte[] snapshotData = new byte[0];
                                                snapshotData = saveConflictData.serverSnapshot.getSnapshotContents().readFully();
                                                String serverData = (snapshotData == null || snapshotData.length == 0) ? "" : new String(snapshotData, StandardCharsets.UTF_8);
                                                long serverTime = saveConflictData.serverSnapshot.getMetadata().getLastModifiedTimestamp();
                                                snapshotData = saveConflictData.localSnapshot.getSnapshotContents().readFully();
                                                String localData = (snapshotData == null || snapshotData.length == 0) ? "" : new String(snapshotData, StandardCharsets.UTF_8);
                                                long localTime = saveConflictData.serverSnapshot.getMetadata().getLastModifiedTimestamp();

                                                result.put("status", ERROR_SNAPSHOT_CONFLICT);
                                                result.put("conflictId", saveConflictData.conflictId);
                                                result.put("serverData", serverData);
                                                result.put("serverTime", serverTime);
                                                result.put("localData", localData);
                                                result.put("localTime", localTime);
                                            } catch (Exception e) {
                                                Log.w(LOGTAG, "executeSaveGame: error on create conflict data", e);
                                            }
                                            callbackContext.error(result);
                                        }
                                    } else {
                                        Exception e = task.getException();
                                        if (e instanceof ApiException) {
                                            ApiException apiException = (ApiException) e;
                                            final Status status = apiException.getStatus();
                                            Log.w(LOGTAG, "executeSaveGame error: " + status.getStatusMessage() + "(" + status.getStatusCode() + ")");
                                            callbackContext.error("executeSaveGame error: " + status.getStatusMessage() + "(" + status.getStatusCode() + ")");
                                        } else {
                                            Log.w(LOGTAG, "executeSaveGame error: " + e.getMessage(), e);
                                            callbackContext.error("executeSaveGame error: " + e.getMessage());
                                        }
                                    }
                                }
                            });
                    } else {
                        Log.w(LOGTAG, "executeSaveGame: not yet signed in");
                        callbackContext.error("executeSaveGame: not yet signed in");
                    }
                } catch (Exception e) {
                    Log.w(LOGTAG, "executeSaveGame: unexpected error", e);
                    callbackContext.error("executeSaveGame: error while open snapshot");
                }
            }
        });
    }

    private void executeLoadGame(final JSONObject options, final CallbackContext callbackContext) {
        Log.d(LOGTAG, "executeLoadGame");

        cordova.getActivity().runOnUiThread(new Runnable() {
            @Override
            public void run() {
                try {
                    if (gameHelper.isSignedIn()) {
                        String saveName = options.getString("saveName");
                        Games.getSnapshotsClient(cordova.getActivity(), gameHelper.getGoogleAccount())
                            .open(saveName, false, Snapshots.RESOLUTION_POLICY_MANUAL)
                            .addOnCompleteListener(new OnCompleteListener<SnapshotsClient.DataOrConflict<Snapshot>>() {
                                @Override
                                public void onComplete(@NonNull Task<SnapshotsClient.DataOrConflict<Snapshot>> task) {
                                    try {
                                        if (task.isSuccessful()) {
                                            SnapshotsClient.DataOrConflict<Snapshot> dataOrConflict = task.getResult();
                                            if (!dataOrConflict.isConflict()) {
                                                Snapshot snapshot = dataOrConflict.getData();
                                                if (snapshot != null && snapshot.getSnapshotContents() != null) {
                                                    SnapshotContents snapshotContents = snapshot.getSnapshotContents();
                                                    byte[] snapshotData = snapshotContents.readFully();
                                                    String saveData = (snapshotData == null || snapshotData.length == 0) ? "" : new String(snapshotData, StandardCharsets.UTF_8);
                                                    Long saveTime = snapshot.getMetadata().getLastModifiedTimestamp();

                                                    JSONObject playerJson = new JSONObject();
                                                    playerJson.put("saveData", saveData);
                                                    playerJson.put("saveTime", saveTime);

                                                    callbackContext.success(playerJson);
                                                } else {
                                                    Log.w(LOGTAG, "executeLoadGame: snapshot or snapshotContents is null");
                                                    try {
                                                        JSONObject errorJson = new JSONObject();
                                                        errorJson.put("status", LOAD_GAME_ERROR_NOT_EXIST);
                                                        errorJson.put("message", "executeLoadGame: snapshot or snapshotContents is null");
                                                        callbackContext.error(errorJson);
                                                    } catch (Exception e) {
                                                        Log.w(LOGTAG, "executeLoadGame: snapshot or snapshotContents is null", e);
                                                        callbackContext.error("executeLoadGame: snapshot or snapshotContents is null");
                                                    }
                                                }
                                            } else {
                                                Log.w(LOGTAG, "executeLoadGame: Conflict on open");
                                                JSONObject result = new JSONObject();
                                                try {
                                                    SnapshotsClient.SnapshotConflict snapshotConflict = dataOrConflict.getConflict();
                                                    saveConflictData = new SaveConflictData();
                                                    saveConflictData.conflictId = snapshotConflict.getConflictId();
                                                    saveConflictData.serverSnapshot = snapshotConflict.getSnapshot();
                                                    saveConflictData.localSnapshot = snapshotConflict.getConflictingSnapshot();
                                                    byte[] snapshotData = new byte[0];
                                                    snapshotData = saveConflictData.serverSnapshot.getSnapshotContents().readFully();
                                                    String serverData = (snapshotData == null || snapshotData.length == 0) ? "" : new String(snapshotData, StandardCharsets.UTF_8);
                                                    long serverTime = saveConflictData.serverSnapshot.getMetadata().getLastModifiedTimestamp();
                                                    snapshotData = saveConflictData.localSnapshot.getSnapshotContents().readFully();
                                                    String localData = (snapshotData == null || snapshotData.length == 0) ? "" : new String(snapshotData, StandardCharsets.UTF_8);
                                                    long localTime = saveConflictData.serverSnapshot.getMetadata().getLastModifiedTimestamp();

                                                    result.put("status", ERROR_SNAPSHOT_CONFLICT);
                                                    result.put("message", "Conflict on open snapshot");
                                                    result.put("conflictId", saveConflictData.conflictId);
                                                    result.put("serverData", serverData);
                                                    result.put("serverTime", serverTime);
                                                    result.put("localData", localData);
                                                    result.put("localTime", localTime);
                                                } catch (Exception e) {
                                                    Log.w(LOGTAG, "executeLoadGame: error on create conflict data", e);
                                                }
                                                callbackContext.error(result);
                                            }
                                        } else {
                                            Exception e = task.getException();
                                            JSONObject result = new JSONObject();
                                            String message;
                                            if (e instanceof ApiException && ((ApiException) e).getStatusCode() == GamesClientStatusCodes.SNAPSHOT_NOT_FOUND) {
                                                ApiException apiException = (ApiException) e;
                                                final Status status = apiException.getStatus();
                                                message = "executeLoadGame error: " + status.getStatusMessage() + "(" + status.getStatusCode() + ")";
                                                Log.w(LOGTAG, message);
                                                result.put("status", LOAD_GAME_ERROR_NOT_EXIST);
                                            } else {
                                                Log.w(LOGTAG, "executeLoadGame error: " + e.getMessage(), e);
                                                result.put("status", LOAD_GAME_ERROR_FAILED);
                                            }
                                            result.put("message", "executeLoadGame error: " + e.getMessage());
                                            callbackContext.error(result);
                                        }
                                    } catch (Exception e) {
                                        Log.w(LOGTAG, "executeLoadGame: unexpected error", e);
                                        try {
                                            JSONObject errorJson = new JSONObject();
                                            errorJson.put("status", LOAD_GAME_ERROR_FAILED);
                                            errorJson.put("message", "executeLoadGame: error while read snapshot");
                                            callbackContext.error(errorJson);
                                        } catch (Exception e2) {
                                            Log.w(LOGTAG, "executeLoadGame: unexpected error", e2);
                                            callbackContext.error("executeLoadGame: error while read snapshot");
                                        }
                                    }
                                }
                            });
                    } else {
                        Log.w(LOGTAG, "executeLoadGame: not yet signed in");
                        try {
                            JSONObject errorJson = new JSONObject();
                            errorJson.put("status", LOAD_GAME_ERROR_NOT_SIGNED);
                            errorJson.put("message", "executeLoadGame: not yet signed in");
                            callbackContext.error(errorJson);
                        } catch (Exception e2) {
                            callbackContext.error("executeLoadGame: not yet signed in");
                        }
                    }
                } catch (Exception e) {
                    Log.w(LOGTAG, "executeLoadGame: unexpected error", e);
                    try {
                        JSONObject errorJson = new JSONObject();
                        errorJson.put("status", LOAD_GAME_ERROR_FAILED);
                        errorJson.put("message", "executeLoadGame: error while opening snapshot");
                        callbackContext.error(errorJson);
                    } catch (Exception e2) {
                        callbackContext.error("executeLoadGame: error while opening snapshot");
                    }
                }
            }
        });
    }

    private void executeResolveSnapshotConflict(final JSONObject options, final CallbackContext callbackContext) {
        cordova.getActivity().runOnUiThread(new Runnable() {
            @Override
            public void run() {
                try {
                    if (saveConflictData == null) {
                        Log.w(LOGTAG, "Have not conflict");
                        callbackContext.error("Have not conflict");
                        return;
                    }
                    Snapshot snapshot = options.optBoolean("useLocal")
                            ? saveConflictData.localSnapshot
                            : saveConflictData.serverSnapshot;
//                    Boolean override = options.optBoolean("override");
                    Games.getSnapshotsClient(cordova.getActivity(), gameHelper.getGoogleAccount())
                        .resolveConflict(saveConflictData.conflictId, snapshot)
                        .addOnCompleteListener(new OnCompleteListener<SnapshotsClient.DataOrConflict<Snapshot>>() {
                            @Override
                            public void onComplete(@NonNull Task<SnapshotsClient.DataOrConflict<Snapshot>> task) {
                                if (task.isSuccessful()) {
                                    SnapshotsClient.DataOrConflict<Snapshot> dataOrConflict = task.getResult();
                                    if (!dataOrConflict.isConflict()) {
                                        callbackContext.success();
    //                        Snapshot snapshot = dataOrConflict.getData();
    //                        PendingResult<Snapshots.CommitSnapshotResult> result = Games.Snapshots.commitAndClose(gameHelper.getApiClient(), snapshot, SnapshotMetadataChange.EMPTY_CHANGE);
    //                        result.setResultCallback(new ResultCallback<Snapshots.CommitSnapshotResult>() {
    //                            @Override
    //                            public void onResult(Snapshots.CommitSnapshotResult commitSnapshotResult) {
    //                                if (commitSnapshotResult.getStatus().isSuccess()) {
    //                                    try {
    //                                        Long saveTime = commitSnapshotResult.getSnapshotMetadata().getLastModifiedTimestamp();
    //                                        JSONObject playerJson = new JSONObject();
    //                                        playerJson.put("saveTime", saveTime);
    //                                        callbackContext.success(playerJson);
    //                                    } catch (Exception e) {
    //                                        callbackContext.success();
    //                                    }
    //                                } else {
    //                                    callbackContext.error("executeResolveSnapshotConflict: save not sent: " + commitSnapshotResult.getStatus().getStatusMessage());
    //                                }
    //                            }
    //                        });
                                    } else {
                                        Log.w(LOGTAG, "executeResolveSnapshotConflict: Conflict on resolve");
                                        JSONObject result = new JSONObject();
                                        try {
                                            SnapshotsClient.SnapshotConflict snapshotConflict = dataOrConflict.getConflict();
                                            saveConflictData = new SaveConflictData();
                                            saveConflictData.conflictId = snapshotConflict.getConflictId();
                                            saveConflictData.serverSnapshot = snapshotConflict.getSnapshot();
                                            saveConflictData.localSnapshot = snapshotConflict.getConflictingSnapshot();
                                            byte[] snapshotData = new byte[0];
                                            snapshotData = saveConflictData.serverSnapshot.getSnapshotContents().readFully();
                                            String serverData = (snapshotData == null || snapshotData.length == 0) ? "" : new String(snapshotData, StandardCharsets.UTF_8);
                                            long serverTime = saveConflictData.serverSnapshot.getMetadata().getLastModifiedTimestamp();
                                            snapshotData = saveConflictData.localSnapshot.getSnapshotContents().readFully();
                                            String localData = (snapshotData == null || snapshotData.length == 0) ? "" : new String(snapshotData, StandardCharsets.UTF_8);
                                            long localTime = saveConflictData.serverSnapshot.getMetadata().getLastModifiedTimestamp();

                                            result.put("status", ERROR_SNAPSHOT_CONFLICT);
                                            result.put("message", "Conflict on resolve");
                                            result.put("conflictId", saveConflictData.conflictId);
                                            result.put("serverData", serverData);
                                            result.put("serverTime", serverTime);
                                            result.put("localData", localData);
                                            result.put("localTime", localTime);
                                        } catch (Exception e) {
                                            Log.w(LOGTAG, "executeResolveSnapshotConflict: error on create conflict data", e);
                                        }
                                        callbackContext.error(result);
                                    }
                                } else {
                                    Exception e = task.getException();
                                    if (e instanceof ApiException) {
                                        ApiException apiException = (ApiException) e;
                                        final Status status = apiException.getStatus();
                                        Log.w(LOGTAG, "executeResolveSnapshotConflict error: " + status.getStatusMessage() + "(" + status.getStatusCode() + ")");
                                        callbackContext.error("executeResolveSnapshotConflict error: " + status.getStatusMessage() + "(" + status.getStatusCode() + ")");
                                    } else {
                                        Log.w(LOGTAG, "executeResolveSnapshotConflict error: " + e.getMessage(), e);
                                        callbackContext.error("executeResolveSnapshotConflict error: " + e.getMessage());
                                    }
                                }
                            }
                        });
                    saveConflictData = null;
                } catch (Exception e) {
                    Log.w(LOGTAG, "executeResolveSnapshotConflict error: " + e.getMessage(), e);
                    callbackContext.error("executeResolveSnapshotConflict error: " + e.getMessage());
                }
            }
        });
    }

    private void executeDeleteSaveGame(final JSONObject options, final CallbackContext callbackContext) {
        Log.d(LOGTAG, "executeDeleteSaveGame");

        cordova.getActivity().runOnUiThread(new Runnable() {
            @Override
            public void run() {
                try {
                    if (gameHelper.isSignedIn()) {
                        String saveName = options.getString("saveName");
                        Games.getSnapshotsClient(cordova.getActivity(), gameHelper.getGoogleAccount())
                            .open(saveName, false, Snapshots.RESOLUTION_POLICY_MOST_RECENTLY_MODIFIED)
                            .addOnCompleteListener(new OnCompleteListener<SnapshotsClient.DataOrConflict<Snapshot>>() {
                                @Override
                                public void onComplete(@NonNull Task<SnapshotsClient.DataOrConflict<Snapshot>> task) {
                                    try {
                                        if (task.isSuccessful()) {
                                            SnapshotsClient.DataOrConflict<Snapshot> dataOrConflict = task.getResult();
                                            if (!dataOrConflict.isConflict()) {
                                                Snapshot snapshot = dataOrConflict.getData();
                                                if (snapshot != null) {
                                                    Games.getSnapshotsClient(cordova.getActivity(), gameHelper.getGoogleAccount())
                                                        .discardAndClose(snapshot)
                                                        .continueWithTask(new Continuation<Void, Task<String>>() {
                                                            @Override
                                                            public Task<String> then(@NonNull Task<Void> task) throws Exception {
                                                                return Games.getSnapshotsClient(cordova.getActivity(), gameHelper.getGoogleAccount())
                                                                        .delete(snapshot.getMetadata());
                                                            }
                                                        }).addOnCompleteListener(new OnCompleteListener<String>() {
                                                            @Override
                                                            public void onComplete(@NonNull Task<String> task) {
                                                                if (task.isSuccessful()) {
                                                                    callbackContext.success();
                                                                } else {
                                                                    Exception e = task.getException();
                                                                    if (e instanceof ApiException) {
                                                                        ApiException apiException = (ApiException) e;
                                                                        final Status status = apiException.getStatus();
                                                                        Log.w(LOGTAG, "executeDeleteSaveGame error: " + status.getStatusMessage() + "(" + status.getStatusCode() + ")");
                                                                        callbackContext.error("executeDeleteSaveGame error: " + status.getStatusMessage() + "(" + status.getStatusCode() + ")");
                                                                    } else {
                                                                        Log.w(LOGTAG, "executeDeleteSaveGame error: " + e.getMessage(), e);
                                                                        callbackContext.error("executeDeleteSaveGame error: " + e.getMessage());
                                                                    }
                                                                }
                                                            }
                                                        });
                                                } else {
                                                    Log.w(LOGTAG, "executeDeleteSaveGame: snapshot is null");
                                                    callbackContext.error("executeDeleteSaveGame: snapshot is null");
                                                }
                                            }
                                        } else {
                                            Exception e = task.getException();
                                            if (e instanceof ApiException) {
                                                ApiException apiException = (ApiException) e;
                                                final Status status = apiException.getStatus();
                                                Log.w(LOGTAG, "executeDeleteSaveGame error: " + status.getStatusMessage() + "(" + status.getStatusCode() + ")");
                                                callbackContext.error("executeDeleteSaveGame error: " + status.getStatusMessage() + "(" + status.getStatusCode() + ")");
                                            } else {
                                                Log.w(LOGTAG, "executeDeleteSaveGame error: " + e.getMessage(), e);
                                                callbackContext.error("executeDeleteSaveGame error: " + e.getMessage());
                                            }
                                        }
                                    } catch (Exception e) {
                                        Log.w(LOGTAG, "executeDeleteSaveGame: unexpected error", e);
                                        callbackContext.error("executeDeleteSaveGame: error while deleting snapshot");
                                    }
                                }
                            });
                    } else {
                        Log.w(LOGTAG, "executeDeleteSaveGame: not yet signed in");
                        callbackContext.error("executeDeleteSaveGame: not yet signed in");
                    }
                } catch (Exception e) {
                    Log.w(LOGTAG, "executeDeleteSaveGame: unexpected error", e);
                    callbackContext.error("executeDeleteSaveGame: error while opening snapshot");
                }
            }
        });
    }

    @Override
    public void onSignInFailed(Boolean userCancel) {
        Log.w(LOGTAG, "SIGN IN FAILED" + (userCancel ? " BY USER CANCEL" : " BY ERROR"));
        JSONObject result = new JSONObject();
        try {
            result.put("userCancel", userCancel);
            result.put("message", "SIGN IN FAILED");
        } catch (JSONException e) {
            Log.w(LOGTAG, "Unknown error", e);
        }
        authCallbackContext.error(result);
    }

    @Override
    public void onSignInSucceeded() {
        Log.w(LOGTAG, "SIGN IN SUCCESS");
        authCallbackContext.success("SIGN IN SUCCESS");
    }

    @Override
    public void setActivityResultCallback() {
        cordova.setActivityResultCallback(this);
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent intent) {
        gameHelper.onActivityResult(requestCode, resultCode, intent);
    }

    @Override
    public void onStop() {
        super.onStop();
        if (gameHelper != null) {
            gameHelper.onStop();
        }
    }

    private class SaveConflictData {
        public String conflictId;
        public Snapshot serverSnapshot;
        public Snapshot localSnapshot;
    }
}
