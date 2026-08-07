const { restoreSession } = require("./utils/auth");

App({
  globalData: {
    session: null,
    activeSessionId: null,
    currentLocation: null
  },

  onLaunch() {
    const session = restoreSession();
    if (session) {
      this.globalData.session = session;
    }
  }
});
