var exec = require('cordova/exec');
var PLAY_GAMES_SERVICES = 'PlayGamesServices';

var PlayGamesServices = function () {
    this.name = PLAY_GAMES_SERVICES;
};

var actions = ['auth', 'signOut', 'isSignedIn',
               'submitScore', 'submitScoreNow', 'getPlayerScore', 'showAllLeaderboards', 'showLeaderboard',
               'unlockAchievement', 'unlockAchievementNow', 'incrementAchievement', 'incrementAchievementNow',
               'showAchievements', 'showPlayer', 'saveGame', 'resolveSnapshotConflict', 'loadGame', 'deleteSaveGame'];

actions.forEach(function (action) {
    PlayGamesServices.prototype[action] = function (data, success, failure) {
        var defaultSuccessCallback = function () {
                console.log(PLAY_GAMES_SERVICES + '.' + action + ': executed successfully');
            };

        var defaultFailureCallback = function () {
                console.warn(PLAY_GAMES_SERVICES + '.' + action + ': failed on execution');
            };

        if (typeof data === 'function') {
            // Assume providing successCallback as 1st arg and possibly failureCallback as 2nd arg
            failure = success || defaultFailureCallback;
            success = data;
            data = {};
        } else {
            data = data || {};
            success = success || defaultSuccessCallback;
            failure = failure || defaultFailureCallback;
        }

        exec(success, failure, PLAY_GAMES_SERVICES, action, [data]);
    };
});

PlayGamesServices.prototype.OK = -1;
PlayGamesServices.prototype.NOT_SIGN_IN = 0;
PlayGamesServices.prototype.LOAD_GAME_ERROR_NOT_EXIST = 11;
PlayGamesServices.prototype.SAVE_GAME_ERROR_WRONG_PREVIOUS_SAVE = 20;
PlayGamesServices.prototype.ERROR_SNAPSHOT_CONFLICT = 30;
PlayGamesServices.prototype.ERROR_HAVE_NOT_SNAPSHOT_CONFLICT = 31;
PlayGamesServices.prototype.GOOGLE_PLAY_ERROR = 254;
PlayGamesServices.prototype.UNKNOWN_ERROR = 255;


PlayGamesServices.prototype.RESOLUTION_POLICY_MANUAL = -1;
PlayGamesServices.prototype.RESOLUTION_POLICY_LONGEST_PLAYTIME = 1;
PlayGamesServices.prototype.RESOLUTION_POLICY_LAST_KNOWN_GOOD = 2;
PlayGamesServices.prototype.RESOLUTION_POLICY_MOST_RECENTLY_MODIFIED = 3;
PlayGamesServices.prototype.RESOLUTION_POLICY_HIGHEST_PROGRESS = 4;

module.exports = new PlayGamesServices();
