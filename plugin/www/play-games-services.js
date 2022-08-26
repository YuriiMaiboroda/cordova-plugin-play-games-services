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

PlayGamesServices.prototype.LOAD_GAME_ERROR_FAILED = 0;
PlayGamesServices.prototype.LOAD_GAME_ERROR_NOT_EXIST = 1;
PlayGamesServices.prototype.LOAD_GAME_ERROR_NOT_SIGNED = 2;

PlayGamesServices.prototype.ERROR_SNAPSHOT_CONFLICT = 3;

PlayGamesServices.prototype.SAVE_GAME_ERROR_WRONG_PREVIOUSE_SAVE = 4;

module.exports = new PlayGamesServices();
