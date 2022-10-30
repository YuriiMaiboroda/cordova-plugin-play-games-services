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
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.util.Log;

import com.berriart.cordova.plugins.GameHelper.GameHelperListener;
import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.GoogleApiAvailability;
import com.google.android.gms.common.api.ApiException;
import com.google.android.gms.common.api.Status;
import com.google.android.gms.common.images.ImageManager;
import com.google.android.gms.games.AnnotatedData;
import com.google.android.gms.games.Games;
import com.google.android.gms.games.GamesClientStatusCodes;
import com.google.android.gms.games.Player;
import com.google.android.gms.games.SnapshotsClient;
import com.google.android.gms.games.SnapshotsClient.SnapshotConflict;
import com.google.android.gms.games.leaderboard.LeaderboardScore;
import com.google.android.gms.games.leaderboard.LeaderboardVariant;
import com.google.android.gms.games.leaderboard.ScoreSubmissionData;
import com.google.android.gms.games.snapshot.Snapshot;
import com.google.android.gms.games.snapshot.SnapshotContents;
import com.google.android.gms.games.snapshot.SnapshotMetadata;
import com.google.android.gms.games.snapshot.SnapshotMetadataChange;

import org.apache.cordova.CallbackContext;
import org.apache.cordova.CordovaInterface;
import org.apache.cordova.CordovaPlugin;
import org.apache.cordova.CordovaWebView;
import org.apache.cordova.PluginResult;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.nio.Buffer;
import java.nio.charset.StandardCharsets;

public class PlayGamesServices extends CordovaPlugin implements GameHelperListener {

    private static final String LOGTAG = "CordovaPlayGamesService";

    private static final String ACTION_AUTH = "auth";
    private static final String ACTION_SIGN_OUT = "signOut";
    private static final String ACTION_IS_SIGNED_IN = "isSignedIn";

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

    public static final int RESOLUTION_POLICY_MANUAL = -1;
    public static final int RESOLUTION_POLICY_LONGEST_PLAYTIME = 1;
    public static final int RESOLUTION_POLICY_LAST_KNOWN_GOOD = 2;
    public static final int RESOLUTION_POLICY_MOST_RECENTLY_MODIFIED = 3;
    public static final int RESOLUTION_POLICY_HIGHEST_PROGRESS = 4;

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
            gameHelper = new GameHelper(GameHelper.CLIENT_ALL);
            if ((cordova.getContext().getApplicationInfo().flags & ApplicationInfo.FLAG_DEBUGGABLE) != 0) {
                gameHelper.enableDebugLog(true);
            }
            gameHelper.setup(this, cordovaActivity);
        } else {
            Log.w(LOGTAG, "GooglePlayServices not available. Error: '" +
                    GoogleApiAvailability.getInstance().getErrorString(googlePlayServicesReturnCode) +
                    "'. Error Code: " + googlePlayServicesReturnCode);
        }

        cordova.setActivityResultCallback(this);
    }

    @Override
    public void onStart() {
        super.onStart();
        if (gameHelper != null) {
            gameHelper.onStart(cordova.getActivity());
        }
    }

    @Override
    public boolean execute(String action, JSONArray inputs, CallbackContext callbackContext) throws JSONException {

        JSONObject options = inputs.optJSONObject(0);

        if (gameHelper == null) {
            Log.w(LOGTAG, "Tried calling: '" + action + "', but error with GooglePlayServices");
            Log.w(LOGTAG, "GooglePlayServices not available. Error: '" +
                    GoogleApiAvailability.getInstance().getErrorString(googlePlayServicesReturnCode) +
                    "'. Error Code: " + googlePlayServicesReturnCode);

            try {
                JSONObject result = new JSONObject();
                JSONObject googlePlayError = new JSONObject();
                googlePlayError.put("errorCode", googlePlayServicesReturnCode);
                googlePlayError.put("errorString", GoogleApiAvailability.getInstance().getErrorString(googlePlayServicesReturnCode));
                result.put("googlePlayError", googlePlayError);
                sendCordovaMessage(callbackContext, PlayGamesServicesErrorCodes.GOOGLE_PLAY_ERROR, "GooglePlayServices not available", result);
            } catch (JSONException e) {
                sendCordovaMessageByException(callbackContext, e);
            }

            return true;
        }

        Log.i(LOGTAG, "Processing action " + action + " ...");

        if (ACTION_AUTH.equals(action)) {
            executeAuth(options, callbackContext);
        } else if (ACTION_SIGN_OUT.equals(action)) {
            executeSignOut(callbackContext);
        } else if (ACTION_IS_SIGNED_IN.equals(action)) {
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
        boolean silent = options.optBoolean("silent");

        Log.d(LOGTAG, "executeAuth" + (silent ? " silent" : ""));
        cordova.getActivity().runOnUiThread(() -> {
            if (silent) {
                gameHelper.silentSignIn();
            } else {
                gameHelper.beginUserInitiatedSignIn(cordova.getActivity());
            }
        });
    }

    private void executeSignOut(final CallbackContext callbackContext) {
        Log.d(LOGTAG, "executeSignOut");

        cordova.getActivity().runOnUiThread(() -> {
            gameHelper.signOut();
            callbackContext.success();
        });
    }

    private void executeIsSignedIn(final CallbackContext callbackContext) {
        Log.d(LOGTAG, "executeIsSignedIn");

        cordova.getActivity().runOnUiThread(() -> {
            final boolean signedIn = gameHelper.isSignedIn(cordova.getActivity());
            try {
                JSONObject result = new JSONObject();
                result.put("isSignedIn", signedIn);
                sendCordovaMessage(callbackContext, PlayGamesServicesErrorCodes.OK, signedIn ? "Signed in" : "Not signed in", result);
            } catch (JSONException e) {
                sendCordovaMessageByException(callbackContext, e);
            }
        });
    }

    private void executeSubmitScore(final JSONObject options, final CallbackContext callbackContext) {
        Log.d(LOGTAG, "executeSubmitScore");

        cordova.getActivity().runOnUiThread(() -> {
            if (!gameHelper.isSignedIn(cordova.getActivity())) {
                sendCordovaMessage(callbackContext, PlayGamesServicesErrorCodes.NOT_SIGN_IN, "executeSubmitScore: not yet signed in");
                return;
            }
            final String leaderboardId;
            final long score;
            try {
                leaderboardId = options.getString("leaderboardId");
                score = options.getLong("score");
            } catch (JSONException e) {
                sendCordovaMessageByException(callbackContext, e);
                return;
            }
            Games.getLeaderboardsClient(cordova.getActivity(), gameHelper.getGoogleAccount(cordova.getActivity()))
                .submitScore(leaderboardId, score);

            sendCordovaMessage(callbackContext, PlayGamesServicesErrorCodes.OK, "executeSubmitScore: score submitted successfully");
        });
    }

    private void executeSubmitScoreNow(final JSONObject options, final CallbackContext callbackContext) {
        Log.d(LOGTAG, "executeSubmitScoreNow");

        cordova.getActivity().runOnUiThread(() -> {
            if (!gameHelper.isSignedIn(cordova.getActivity())) {
                sendCordovaMessage(callbackContext, PlayGamesServicesErrorCodes.NOT_SIGN_IN, "executeSubmitScoreNow: not yet signed in");
                return;
            }
            final String leaderboardId;
            final long score;
            try {
                leaderboardId = options.getString("leaderboardId");
                score = options.getLong("score");
            } catch (JSONException e) {
                sendCordovaMessageByException(callbackContext, e);
                return;
            }
            Games.getLeaderboardsClient(cordova.getActivity(), gameHelper.getGoogleAccount(cordova.getActivity()))
                .submitScoreImmediate(leaderboardId, score)
                .addOnCompleteListener(task -> {
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
                                sendCordovaMessage(callbackContext, PlayGamesServicesErrorCodes.OK, "", result);
                            } catch (JSONException e) {
                                sendCordovaMessageByException(callbackContext, e);
                            }
                        } else {
                            sendCordovaMessage(callbackContext, PlayGamesServicesErrorCodes.UNKNOWN_ERROR, "executeSubmitScoreNow: can't submit the score");
                        }
                    } else {
                        sendCordovaMessageByException(callbackContext, task.getException());
                    }
                });
        });
    }

    private void executeGetPlayerScore(final JSONObject options, final CallbackContext callbackContext) {
        Log.d(LOGTAG, "executeGetPlayerScore");

        cordova.getActivity().runOnUiThread(() -> {
            if (!gameHelper.isSignedIn(cordova.getActivity())) {
                sendCordovaMessage(callbackContext, PlayGamesServicesErrorCodes.NOT_SIGN_IN, "executeGetPlayerScore: not yet signed in");
                return;
            }
            final String leaderboardId;
            try {
                leaderboardId = options.getString("leaderboardId");
            } catch (JSONException e) {
                sendCordovaMessageByException(callbackContext, e);
                return;
            }
            Games.getLeaderboardsClient(cordova.getActivity(), gameHelper.getGoogleAccount(cordova.getActivity()))
                .loadCurrentPlayerLeaderboardScore(leaderboardId, LeaderboardVariant.TIME_SPAN_ALL_TIME, LeaderboardVariant.COLLECTION_PUBLIC)
                .addOnCompleteListener(task -> {
                    if (task.isSuccessful()) {
                        AnnotatedData<LeaderboardScore> scoreAnnotatedData = task.getResult();
                        LeaderboardScore score = scoreAnnotatedData.get();
                        if (score != null) {
                            try {
                                JSONObject result = new JSONObject();
                                result.put("playerScore", score.getRawScore());
                                result.put("playerRank", score.getRank());
                                result.put("isStale", scoreAnnotatedData.isStale());
                                sendCordovaMessage(callbackContext, PlayGamesServicesErrorCodes.OK, "", result);
                            } catch (JSONException e) {
                                sendCordovaMessageByException(callbackContext, e);
                            }
                        } else {
                            sendCordovaMessage(callbackContext, PlayGamesServicesErrorCodes.UNKNOWN_ERROR, "There isn't have any score record for this player");
                        }
                    } else {
                        sendCordovaMessageByException(callbackContext, task.getException());
                    }
                });
        });
    }

    private void executeShowAllLeaderboards(final CallbackContext callbackContext) {
        Log.d(LOGTAG, "executeShowAllLeaderboards");

        final PlayGamesServices plugin = this;

        cordova.getActivity().runOnUiThread(() -> {
            if (!gameHelper.isSignedIn(cordova.getActivity())) {
                sendCordovaMessage(callbackContext, PlayGamesServicesErrorCodes.NOT_SIGN_IN, "executeShowAllLeaderboards: not yet signed in");
                return;
            }
            Games.getLeaderboardsClient(cordova.getActivity(), gameHelper.getGoogleAccount(cordova.getActivity()))
                .getAllLeaderboardsIntent()
                .addOnCompleteListener(task -> {
                    if (task.isSuccessful()) {
                        Intent allLeaderboardsIntent = task.getResult();
                        cordova.startActivityForResult(plugin, allLeaderboardsIntent, ACTIVITY_CODE_SHOW_LEADERBOARD);
                        sendCordovaMessage(callbackContext, PlayGamesServicesErrorCodes.OK, "");
                    } else {
                        sendCordovaMessageByException(callbackContext, task.getException());
                    }
                });
        });
    }

    private void executeShowLeaderboard(final JSONObject options, final CallbackContext callbackContext) {
        Log.d(LOGTAG, "executeShowLeaderboard");

        final PlayGamesServices plugin = this;

        cordova.getActivity().runOnUiThread(() -> {
            if (!gameHelper.isSignedIn(cordova.getActivity())) {
                sendCordovaMessage(callbackContext, PlayGamesServicesErrorCodes.NOT_SIGN_IN, "executeShowLeaderboard: not yet signed in");
                return;
            }
            final String leaderboardId;
            try {
                leaderboardId = options.getString("leaderboardId");
            } catch (JSONException e) {
                sendCordovaMessageByException(callbackContext, e);
                return;
            }
            Games.getLeaderboardsClient(cordova.getActivity(), gameHelper.getGoogleAccount(cordova.getActivity()))
                .getLeaderboardIntent(leaderboardId)
                .addOnCompleteListener(task -> {
                    if (task.isSuccessful()) {
                        Intent leaderboardIntent = task.getResult();
                        cordova.startActivityForResult(plugin, leaderboardIntent, ACTIVITY_CODE_SHOW_LEADERBOARD);
                        sendCordovaMessage(callbackContext, PlayGamesServicesErrorCodes.OK, "");
                    } else {
                        sendCordovaMessageByException(callbackContext, task.getException());
                    }
                });
        });
    }

    private void executeUnlockAchievement(final JSONObject options, final CallbackContext callbackContext) {
        Log.d(LOGTAG, "executeUnlockAchievement");

        cordova.getActivity().runOnUiThread(() -> {
            if (!gameHelper.isSignedIn(cordova.getActivity())) {
                sendCordovaMessage(callbackContext, PlayGamesServicesErrorCodes.NOT_SIGN_IN, "executeUnlockAchievement: not yet signed in");
                return;
            }
            final String achievementId;
            try {
                achievementId = options.getString("achievementId");
            } catch (JSONException e) {
                sendCordovaMessageByException(callbackContext, e);
                return;
            }
            Games.getAchievementsClient(cordova.getActivity(), gameHelper.getGoogleAccount(cordova.getActivity())).unlock(achievementId);
            sendCordovaMessage(callbackContext, PlayGamesServicesErrorCodes.OK, "");
        });
    }

    private void executeUnlockAchievementNow(final JSONObject options, final CallbackContext callbackContext) {
        Log.d(LOGTAG, "executeUnlockAchievementNow");

        cordova.getActivity().runOnUiThread(() -> {
            if (!gameHelper.isSignedIn(cordova.getActivity())) {
                sendCordovaMessage(callbackContext, PlayGamesServicesErrorCodes.NOT_SIGN_IN, "executeUnlockAchievementNow: not yet signed in");
                return;
            }
            final String achievementId;
            try {
                achievementId = options.getString("achievementId");
            } catch (JSONException e) {
                sendCordovaMessageByException(callbackContext, e);
                return;
            }
            Games.getAchievementsClient(cordova.getActivity(), gameHelper.getGoogleAccount(cordova.getActivity()))
                .unlockImmediate(achievementId)
                .addOnCompleteListener(task -> {
                    if (task.isSuccessful()) {
                        try {
                            JSONObject result = new JSONObject();
                            result.put("achievementId", achievementId);
                            sendCordovaMessage(callbackContext, PlayGamesServicesErrorCodes.OK, "", result);
                        } catch (JSONException e) {
                            sendCordovaMessageByException(callbackContext, e);
                        }
                    } else {
                        sendCordovaMessageByException(callbackContext, task.getException());
                    }
                });
        });
    }

    private void executeIncrementAchievement(final JSONObject options, final CallbackContext callbackContext) {
        Log.d(LOGTAG, "executeIncrementAchievement");

        cordova.getActivity().runOnUiThread(() -> {
            if (!gameHelper.isSignedIn(cordova.getActivity())) {
                sendCordovaMessage(callbackContext, PlayGamesServicesErrorCodes.NOT_SIGN_IN, "executeIncrementAchievement: not yet signed in");
                return;
            }
            final String achievementId;
            final int numSteps;
            try {
                achievementId = options.getString("achievementId");
                numSteps = options.getInt("numSteps");
            } catch (JSONException e) {
                sendCordovaMessageByException(callbackContext, e);
                return;
            }
            Games.getAchievementsClient(cordova.getActivity(), gameHelper.getGoogleAccount(cordova.getActivity())).increment(achievementId, numSteps);
            sendCordovaMessage(callbackContext, PlayGamesServicesErrorCodes.OK, "");
        });
    }

    private void executeIncrementAchievementNow(final JSONObject options, final CallbackContext callbackContext) {
        Log.d(LOGTAG, "executeIncrementAchievementNow");

        cordova.getActivity().runOnUiThread(() -> {
            if (!gameHelper.isSignedIn(cordova.getActivity())) {
                sendCordovaMessage(callbackContext, PlayGamesServicesErrorCodes.NOT_SIGN_IN, "executeIncrementAchievement: not yet signed in");
                return;
            }
            final String achievementId;
            final int numSteps;
            try {
                achievementId = options.getString("achievementId");
                numSteps = options.getInt("numSteps");
            } catch (JSONException e) {
                sendCordovaMessageByException(callbackContext, e);
                return;
            }
            Games.getAchievementsClient(cordova.getActivity(), gameHelper.getGoogleAccount(cordova.getActivity()))
                .incrementImmediate(achievementId, numSteps)
                .addOnCompleteListener(task -> {
                    if (task.isSuccessful()) {
                        try {
                            Boolean unlocked = task.getResult();
                            JSONObject result = new JSONObject();
                            result.put("achievementId", achievementId);
                            result.put("isUnlocked", unlocked);
                            sendCordovaMessage(callbackContext, PlayGamesServicesErrorCodes.OK, "", result);
                        } catch (JSONException e) {
                            sendCordovaMessageByException(callbackContext, e);
                        }
                    } else {
                        sendCordovaMessageByException(callbackContext, task.getException());
                    }
                });
        });
    }

    private void executeShowAchievements(final CallbackContext callbackContext) {
        Log.d(LOGTAG, "executeShowAchievements");

        final PlayGamesServices plugin = this;

        cordova.getActivity().runOnUiThread(() -> {
            if (!gameHelper.isSignedIn(cordova.getActivity())) {
                sendCordovaMessage(callbackContext, PlayGamesServicesErrorCodes.NOT_SIGN_IN, "executeShowAchievements: not yet signed in");
                return;
            }
            Games.getAchievementsClient(cordova.getActivity(), gameHelper.getGoogleAccount(cordova.getActivity()))
                .getAchievementsIntent()
                .addOnCompleteListener(task -> {
                    if (task.isSuccessful()) {
                        Intent achievementsIntent = task.getResult();
                        cordova.startActivityForResult(plugin, achievementsIntent, ACTIVITY_CODE_SHOW_ACHIEVEMENTS);
                        sendCordovaMessage(callbackContext, PlayGamesServicesErrorCodes.OK, "");
                    } else {
                        sendCordovaMessageByException(callbackContext, task.getException());
                    }
                });
        });
    }

    private void executeShowPlayer(final CallbackContext callbackContext) {
        Log.d(LOGTAG, "executeShowPlayer");
        cordova.getActivity().runOnUiThread(() -> {
            if (!gameHelper.isSignedIn(cordova.getActivity())) {
                sendCordovaMessage(callbackContext, PlayGamesServicesErrorCodes.NOT_SIGN_IN, "executeShowPlayer: not yet signed in");
                return;
            }
            Games.getPlayersClient(cordova.getActivity(), gameHelper.getGoogleAccount(cordova.getActivity()))
                .getCurrentPlayer()
                .addOnCompleteListener(task -> {
                    if (task.isSuccessful()) {
                        Player player = task.getResult();
                        try {
                            JSONObject result = new JSONObject();
                            result.put("displayName", player.getDisplayName());
                            result.put("playerId", player.getPlayerId());
                            result.put("title", player.getTitle());
                            result.put("iconImageUri", player.getIconImageUri() != null ? player.getIconImageUri().toString() : "");
                            result.put("iconImageUrl", player.getIconImageUrl() != null ? player.getIconImageUrl() : "");
                            result.put("hiResIconImageUri", player.getHiResImageUri() != null ? player.getHiResImageUri().toString() : "");
                            result.put("hiResIconImageUrl", player.getHiResImageUrl() != null ? player.getHiResImageUrl() : "");


//                            ImageManager imageManager = ImageManager.create(cordova.getContext());
//                            imageManager.loadImage(new ImageManager.OnImageLoadedListener() {
//                                @Override
//                                public void onImageLoaded(Uri uri, Drawable drawable, boolean isRequestedDrawable) {
//                                    if (isRequestedDrawable) {
//                                        Bitmap bitmap = drawableToBitmap(drawable);
//                                        int[] pixels = new int[bitmap.getWidth() * bitmap.getHeight()];
//                                        bitmap.getPixels(pixels, 0, 0, 0, 0, bitmap.getWidth(), bitmap.getHeight());
//                                    }
//                                }
//                            }, player.getIconImageUri());

                            sendCordovaMessage(callbackContext, PlayGamesServicesErrorCodes.OK, "", result);
                        } catch (JSONException e) {
                            sendCordovaMessageByException(callbackContext, e);
                        }
                    } else {
                        sendCordovaMessageByException(callbackContext, task.getException());
                    }
                });
        });
    }
    private static Bitmap drawableToBitmap (Drawable drawable) {
        Bitmap bitmap = null;

        if (drawable instanceof BitmapDrawable) {
            BitmapDrawable bitmapDrawable = (BitmapDrawable) drawable;
            if(bitmapDrawable.getBitmap() != null) {
                return bitmapDrawable.getBitmap();
            }
        }

        if(drawable.getIntrinsicWidth() <= 0 || drawable.getIntrinsicHeight() <= 0) {
            bitmap = Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888); // Single color bitmap will be created of 1x1 pixel
        } else {
            bitmap = Bitmap.createBitmap(drawable.getIntrinsicWidth(), drawable.getIntrinsicHeight(), Bitmap.Config.ARGB_8888);
        }

        Canvas canvas = new Canvas(bitmap);
        drawable.setBounds(0, 0, canvas.getWidth(), canvas.getHeight());
        drawable.draw(canvas);
        return bitmap;
    }

    private void executeSaveGame(final JSONObject options, final CallbackContext callbackContext) {
        Log.d(LOGTAG, "executeSaveGame");
        cordova.getActivity().runOnUiThread(() -> {
            if (!gameHelper.isSignedIn(cordova.getActivity())) {
                sendCordovaMessage(callbackContext, PlayGamesServicesErrorCodes.NOT_SIGN_IN, "executeSaveGame: not yet signed in");
                return;
            }
            final String saveName;
            final String saveData;
            final long previousSaveTime;
            final int resolutionPolicy;
            try {
                saveName = options.getString("saveName");
                saveData = options.getString("saveData");
                previousSaveTime = options.optLong("previousSaveTime", -1);

                final int resolutionPolicyTmp = options.optInt("resolutionPolicy", RESOLUTION_POLICY_MANUAL);
                switch (resolutionPolicyTmp) {
                    case RESOLUTION_POLICY_MANUAL: {
                        resolutionPolicy = SnapshotsClient.RESOLUTION_POLICY_MANUAL;
                        break;
                    }
                    case RESOLUTION_POLICY_HIGHEST_PROGRESS: {
                        resolutionPolicy = SnapshotsClient.RESOLUTION_POLICY_HIGHEST_PROGRESS;
                        break;
                    }
                    case RESOLUTION_POLICY_LAST_KNOWN_GOOD: {
                        resolutionPolicy = SnapshotsClient.RESOLUTION_POLICY_LAST_KNOWN_GOOD;
                        break;
                    }
                    case RESOLUTION_POLICY_LONGEST_PLAYTIME: {
                        resolutionPolicy = SnapshotsClient.RESOLUTION_POLICY_LONGEST_PLAYTIME;
                        break;
                    }
                    case RESOLUTION_POLICY_MOST_RECENTLY_MODIFIED: {
                        resolutionPolicy = SnapshotsClient.RESOLUTION_POLICY_MOST_RECENTLY_MODIFIED;
                        break;
                    }
                    default: {
                        resolutionPolicy = SnapshotsClient.RESOLUTION_POLICY_MANUAL;
                    }
                }
            } catch (JSONException e) {
                sendCordovaMessageByException(callbackContext, e);
                return;
            }
            Games.getSnapshotsClient(cordova.getActivity(), gameHelper.getGoogleAccount(cordova.getActivity()))
                .open(saveName, true, resolutionPolicy)
                .addOnCompleteListener(task -> {
                    if (task.isSuccessful()) {
                        SnapshotsClient.DataOrConflict<Snapshot> dataOrConflict = task.getResult();
                        if (!dataOrConflict.isConflict()) {
                            Snapshot snapshot = dataOrConflict.getData();
                            if (snapshot != null && snapshot.getSnapshotContents() != null) {
                                SnapshotContents snapshotContents = snapshot.getSnapshotContents();
                                byte[] data;
                                try {
                                    data = snapshotContents.readFully();
                                } catch (IOException e) {
                                    sendCordovaMessageByException(callbackContext, e);
                                    return;
                                }
                                if (previousSaveTime == -1 || data == null || data.length == 0 ||  previousSaveTime == snapshot.getMetadata().getLastModifiedTimestamp()) {
                                    snapshotContents.writeBytes(saveData.getBytes(StandardCharsets.UTF_8));
                                    Games.getSnapshotsClient(cordova.getActivity(), gameHelper.getGoogleAccount(cordova.getActivity()))
                                        .commitAndClose(snapshot, SnapshotMetadataChange.EMPTY_CHANGE)
                                        .addOnCompleteListener(task1 -> {
                                            if (task1.isSuccessful()) {
                                                SnapshotMetadata metadata = task1.getResult();
                                                JSONObject metadataJson = null;
                                                JSONObject result = new JSONObject();
                                                try {
                                                    metadataJson = metadataToJson(metadata);
                                                    result.put("metadata", metadataJson);
                                                    sendCordovaMessage(callbackContext, PlayGamesServicesErrorCodes.OK, "", result);
                                                } catch (JSONException e) {
                                                    sendCordovaMessageByException(callbackContext, e);
                                                }
                                            } else {
                                                sendCordovaMessageByException(callbackContext, task.getException());
                                            }
                                        });
                                } else {
                                    Log.w(LOGTAG, "executeSaveGame: wrong previous save");
                                    try {
                                        byte[] snapshotData = snapshotContents.readFully();
                                        String saveData1 = (snapshotData == null || snapshotData.length == 0) ? "" : new String(snapshotData, StandardCharsets.UTF_8);
                                        final SnapshotMetadata metadata = snapshot.getMetadata();
                                        JSONObject metadataJson = metadataToJson(metadata);

                                        JSONObject result = new JSONObject();
                                        result.put("saveData", saveData1);
                                        result.put("metadata", metadataJson);
                                        sendCordovaMessage(callbackContext, PlayGamesServicesErrorCodes.SAVE_GAME_ERROR_WRONG_PREVIOUS_SAVE, "", result);
                                        callbackContext.error(result);
                                    } catch (Exception e) {
                                        sendCordovaMessageByException(callbackContext, e);
                                    }
                                }
                            } else {
                                sendCordovaMessage(callbackContext, PlayGamesServicesErrorCodes.UNKNOWN_ERROR, "executeSaveGame: snapshot or snapshotContents is null");
                            }
                        } else {
                            Log.w(LOGTAG, "executeSaveGame: Conflict on open");
                            SnapshotConflict snapshotConflict = dataOrConflict.getConflict();
                            if (snapshotConflict != null) {
                                JSONObject result = null;
                                try {
                                    result = processSnapshotConflict(snapshotConflict);
                                } catch (JSONException | IOException e) {
                                    sendCordovaMessageByException(callbackContext, e);
                                    return;
                                }
                                sendCordovaMessage(callbackContext, PlayGamesServicesErrorCodes.ERROR_SNAPSHOT_CONFLICT, "executeSaveGame: Conflict on open", result);
                            } else {
                                sendCordovaMessage(callbackContext, PlayGamesServicesErrorCodes.UNKNOWN_ERROR, "executeSaveGame: UNKNOWN ERROR");
                            }
                        }
                    } else {
                        sendCordovaMessageByException(callbackContext, task.getException());
                    }
                });
        });
    }

    private void executeLoadGame(final JSONObject options, final CallbackContext callbackContext) {
        Log.d(LOGTAG, "executeLoadGame");

        cordova.getActivity().runOnUiThread(() -> {
            if (!gameHelper.isSignedIn(cordova.getActivity())) {
                sendCordovaMessage(callbackContext, PlayGamesServicesErrorCodes.NOT_SIGN_IN, "executeLoadGame: not yet signed in");
                return;
            }
            final String saveName;
            try {
                saveName = options.getString("saveName");
            } catch (JSONException e) {
                sendCordovaMessageByException(callbackContext, e);
                return;
            }
            Games.getSnapshotsClient(cordova.getActivity(), gameHelper.getGoogleAccount(cordova.getActivity()))
                .open(saveName, false, SnapshotsClient.RESOLUTION_POLICY_MANUAL)
                .addOnCompleteListener(task -> {
                    if (task.isSuccessful()) {
                        SnapshotsClient.DataOrConflict<Snapshot> dataOrConflict = task.getResult();
                        if (!dataOrConflict.isConflict()) {
                            Snapshot snapshot = dataOrConflict.getData();
                            if (snapshot != null && snapshot.getSnapshotContents() != null) {
                                SnapshotContents snapshotContents = snapshot.getSnapshotContents();
                                byte[] snapshotData;
                                try {
                                    snapshotData = snapshotContents.readFully();
                                } catch (IOException e) {
                                    sendCordovaMessageByException(callbackContext, e);
                                    return;
                                }
                                String saveData = (snapshotData == null || snapshotData.length == 0) ? "" : new String(snapshotData, StandardCharsets.UTF_8);
                                final SnapshotMetadata metadata = snapshot.getMetadata();
                                JSONObject metadataJson = null;
                                JSONObject result = new JSONObject();
                                try {
                                    metadataJson = metadataToJson(metadata);
                                    result.put("saveData", saveData);
                                    result.put("metadata", metadataJson);
                                    sendCordovaMessage(callbackContext, PlayGamesServicesErrorCodes.OK, "", result);
                                } catch (JSONException e) {
                                    sendCordovaMessageByException(callbackContext, e);
                                }
                            } else {
                                sendCordovaMessage(callbackContext, PlayGamesServicesErrorCodes.UNKNOWN_ERROR, "executeLoadGame: snapshot or snapshotContents is null");
                            }
                        } else {
                            Log.w(LOGTAG, "executeLoadGame: Conflict on open");
                            SnapshotConflict snapshotConflict = dataOrConflict.getConflict();
                            if (snapshotConflict != null) {
                                JSONObject result = null;
                                try {
                                    result = processSnapshotConflict(snapshotConflict);
                                } catch (JSONException | IOException e) {
                                    sendCordovaMessageByException(callbackContext, e);
                                    return;
                                }
                                sendCordovaMessage(callbackContext, PlayGamesServicesErrorCodes.ERROR_SNAPSHOT_CONFLICT, "executeLoadGame: Conflict on open", result);
                            } else {
                                sendCordovaMessage(callbackContext, PlayGamesServicesErrorCodes.UNKNOWN_ERROR, "executeLoadGame: UNKNOWN ERROR");
                            }
                        }
                    } else {
                        Exception exception = task.getException();
                        if (exception instanceof ApiException && ((ApiException) exception).getStatusCode() == GamesClientStatusCodes.SNAPSHOT_NOT_FOUND) {
                            sendCordovaMessage(callbackContext, PlayGamesServicesErrorCodes.LOAD_GAME_ERROR_NOT_EXIST, "executeLoadGame error: " + exception.getMessage());
                        } else {
                            sendCordovaMessageByException(callbackContext, exception);
                        }
                    }
                });
        });
    }

    private void executeResolveSnapshotConflict(final JSONObject options, final CallbackContext callbackContext) {
        cordova.getActivity().runOnUiThread(() -> {
            if (!gameHelper.isSignedIn(cordova.getActivity())) {
                sendCordovaMessage(callbackContext, PlayGamesServicesErrorCodes.NOT_SIGN_IN, "executeLoadGame: not yet signed in");
                return;
            }
            if (saveConflictData == null) {
                sendCordovaMessage(callbackContext, PlayGamesServicesErrorCodes.ERROR_HAVE_NOT_SNAPSHOT_CONFLICT, "executeLoadGame: Have not conflict");
                return;
            }
            Snapshot snapshot = options.optBoolean("useLocal")
                    ? saveConflictData.localSnapshot
                    : saveConflictData.serverSnapshot;
//                    Boolean override = options.optBoolean("override");
            Games.getSnapshotsClient(cordova.getActivity(), gameHelper.getGoogleAccount(cordova.getActivity()))
                .resolveConflict(saveConflictData.conflictId, snapshot)
                .addOnCompleteListener(task -> {
                    if (task.isSuccessful()) {
                        SnapshotsClient.DataOrConflict<Snapshot> dataOrConflict = task.getResult();
                        if (!dataOrConflict.isConflict()) {
                            sendCordovaMessage(callbackContext, PlayGamesServicesErrorCodes.OK, "");
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
                            try {
                                SnapshotConflict snapshotConflict = dataOrConflict.getConflict();
                                if (snapshotConflict != null) {
                                    JSONObject result = processSnapshotConflict(snapshotConflict);
                                    sendCordovaMessage(callbackContext, PlayGamesServicesErrorCodes.ERROR_SNAPSHOT_CONFLICT, "executeLoadGame: Conflict on resolve", result);
                                } else {
                                    sendCordovaMessage(callbackContext, PlayGamesServicesErrorCodes.UNKNOWN_ERROR, "executeResolveSnapshotConflict: UNKNOWN ERROR");
                                }
                            } catch (Exception e) {
                                sendCordovaMessageByException(callbackContext, e);
                            }
                        }
                    } else {
                        sendCordovaMessageByException(callbackContext, task.getException());
                    }
                });
            saveConflictData = null;
        });
    }

    private void executeDeleteSaveGame(final JSONObject options, final CallbackContext callbackContext) {
        Log.d(LOGTAG, "executeDeleteSaveGame");

        cordova.getActivity().runOnUiThread(() -> {
            if (!gameHelper.isSignedIn(cordova.getActivity())) {
                sendCordovaMessage(callbackContext, PlayGamesServicesErrorCodes.NOT_SIGN_IN, "executeDeleteSaveGame: not yet signed in");
                return;
            }
            final String saveName;
            try {
                saveName = options.getString("saveName");
            } catch (JSONException e) {
                sendCordovaMessageByException(callbackContext, e);
                return;
            }
            Games.getSnapshotsClient(cordova.getActivity(), gameHelper.getGoogleAccount(cordova.getActivity()))
                .open(saveName, false, SnapshotsClient.RESOLUTION_POLICY_MOST_RECENTLY_MODIFIED)
                .addOnCompleteListener(task -> {
                    if (task.isSuccessful()) {
                        SnapshotsClient.DataOrConflict<Snapshot> dataOrConflict = task.getResult();
                        Snapshot snapshot = dataOrConflict.getData();
                        if (snapshot != null) {
                            Games.getSnapshotsClient(cordova.getActivity(), gameHelper.getGoogleAccount(cordova.getActivity()))
                                .discardAndClose(snapshot)
                                .continueWithTask(
                                    task1 -> Games.getSnapshotsClient(cordova.getActivity(), gameHelper.getGoogleAccount(cordova.getActivity()))
                                        .delete(snapshot.getMetadata())
                                )
                                .addOnCompleteListener(
                                    task12 -> {
                                        if (task12.isSuccessful()) {
                                            sendCordovaMessage(callbackContext, PlayGamesServicesErrorCodes.OK, "");
                                        } else {
                                            sendCordovaMessageByException(callbackContext, task.getException());
                                        }
                                    }
                                );
                        } else {
                            sendCordovaMessage(callbackContext, PlayGamesServicesErrorCodes.UNKNOWN_ERROR, "executeDeleteSaveGame: snapshot is null");
                        }
                    } else {
                        sendCordovaMessageByException(callbackContext, task.getException());
                    }
                });
        });
    }

    private JSONObject metadataToJson(SnapshotMetadata metadata) throws JSONException {
        JSONObject result = new JSONObject();

        long saveTime = metadata.getLastModifiedTimestamp();
        final String title = metadata.getTitle();
        String description = metadata.getDescription();
        float playedTime = metadata.getPlayedTime();
        float progressValue = metadata.getProgressValue();
        float coverImageAspectRatio = metadata.getCoverImageAspectRatio();
        String coverImage = metadata.getCoverImageUri() != null ? metadata.getCoverImageUri().toString() : "";
        String deviceName = metadata.getDeviceName();

        Player player = metadata.getOwner();

        String playerId = player.getPlayerId();
        String playerDisplayName = player.getDisplayName();
        String playerName = player.getName();
        String playerTitle = player.getTitle();
        String playerIconImage = player.getIconImageUri() != null ? player.getIconImageUri().toString() : "";
        String playerHiResImage = player.getHiResImageUri() != null ? player.getHiResImageUri().toString() : "";

        result.put("saveTime", saveTime);
        result.put("title", title);
        result.put("description", description);
        result.put("playedTime", playedTime);
        result.put("progressValue", progressValue);
        result.put("coverImageAspectRatio", coverImageAspectRatio);
        result.put("coverImage", coverImage);
        result.put("deviceName", deviceName);
        result.put("playerId", playerId);
        result.put("playerDisplayName", playerDisplayName);
        result.put("playerName", playerName);
        result.put("playerTitle", playerTitle);
        result.put("playerIconImage", playerIconImage);
        result.put("playerHiResImage", playerHiResImage);

        return result;
    }

    private JSONObject processSnapshotConflict(SnapshotConflict snapshotConflict) throws JSONException, IOException {
        JSONObject result = new JSONObject();

        saveConflictData = new SaveConflictData();
        saveConflictData.conflictId = snapshotConflict.getConflictId();
        saveConflictData.serverSnapshot = snapshotConflict.getSnapshot();
        saveConflictData.localSnapshot = snapshotConflict.getConflictingSnapshot();
        byte[] snapshotData;
        SnapshotMetadata metadata;
        snapshotData = saveConflictData.serverSnapshot.getSnapshotContents().readFully();
        String serverData = (snapshotData == null || snapshotData.length == 0) ? "" : new String(snapshotData, StandardCharsets.UTF_8);
        metadata = saveConflictData.serverSnapshot.getMetadata();
        JSONObject serverMetadataJson = metadataToJson(metadata);
        snapshotData = saveConflictData.localSnapshot.getSnapshotContents().readFully();
        String localData = (snapshotData == null || snapshotData.length == 0) ? "" : new String(snapshotData, StandardCharsets.UTF_8);
        metadata = saveConflictData.serverSnapshot.getMetadata();
        JSONObject localMetadataJson = metadataToJson(metadata);

        result.put("conflictId", saveConflictData.conflictId);
        result.put("serverData", serverData);
        result.put("serverMetadata", serverMetadataJson);
        result.put("localData", localData);
        result.put("localMetadata", localMetadataJson);

        return result;
    }

    @Override
    public void onSignInFailed(Boolean userCancel) {
        Log.w(LOGTAG, "SIGN IN FAILED" + (userCancel ? " BY USER CANCEL" : " BY ERROR"));
        if (authCallbackContext == null) {
            Log.w(LOGTAG, "SIGN IN FAILED: authCallbackContext is null");
            return;
        }
        try {
            JSONObject result = new JSONObject();
            result.put("userCancel", userCancel);
            sendCordovaMessage(authCallbackContext, PlayGamesServicesErrorCodes.SIGN_IN_FAILED, "SIGN IN FAILED", result);
        } catch (JSONException e) {
            sendCordovaMessageByException(authCallbackContext, e);
        }
    }

    @Override
    public void onSignInSucceeded() {
        if (authCallbackContext == null) {
            Log.w(LOGTAG, "SIGN IN SUCCESS: authCallbackContext is null");
            return;
        }
        sendCordovaMessage(authCallbackContext, PlayGamesServicesErrorCodes.OK, "SIGN IN SUCCESS");
    }

    @Override
    public void setActivityResultCallback() {
        cordova.setActivityResultCallback(this);
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent intent) {
        if (gameHelper != null) {
            gameHelper.onActivityResult(requestCode, resultCode, intent);
        }
    }

    @Override
    public void onStop() {
        super.onStop();
        if (gameHelper != null) {
            gameHelper.onStop();
        }
    }

    private void sendCordovaMessage(final CallbackContext callbackContext, int status, String message) {
        sendCordovaMessage(callbackContext, status, message, null);
    }
    private void sendCordovaMessage(final CallbackContext callbackContext, int status, String message, JSONObject result) {
        try {
            PluginResult.Status pluginResultStatus;
            if (status == PlayGamesServicesErrorCodes.OK) {
                Log.d(LOGTAG, message);
                pluginResultStatus = PluginResult.Status.OK;
            } else {
                Log.w(LOGTAG, message);
                pluginResultStatus = PluginResult.Status.ERROR;
            }
            if (result == null) {
                result = new JSONObject();
            }
            result.put("status", status);
            result.put("message", message);


            callbackContext.sendPluginResult(new PluginResult(pluginResultStatus, result));
        } catch (JSONException e) {
            sendCordovaMessageByException(callbackContext, e);
        }
    }

    private void sendCordovaMessageByException(final CallbackContext callbackContext, Exception e) {
        PluginResult.Status status;
        String msg;
        if (e == null) {
            msg = "UNKNOWN ERROR";
            status = PluginResult.Status.ERROR;
        } else if (e instanceof JSONException) {
            msg = "JSONException: " + e.getMessage();
            status = PluginResult.Status.JSON_EXCEPTION;
        } else if (e instanceof IOException) {
            msg = "IOException: " + e.getMessage();
            status = PluginResult.Status.IO_EXCEPTION;
        } else {
            msg = "Exception: " + e.getMessage();
            status = PluginResult.Status.ERROR;
        }
        Log.w(LOGTAG, msg, e);
        callbackContext.sendPluginResult(new PluginResult(status, msg));
    }

    private static class SaveConflictData {
        public String conflictId;
        public Snapshot serverSnapshot;
        public Snapshot localSnapshot;
    }
}
