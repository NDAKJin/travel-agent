const SESSION_KEY = "travel-agent-mini-session";

function getSessionStorage() {
  try {
    return wx.getStorageSync(SESSION_KEY) || null;
  } catch (error) {
    return null;
  }
}

function setSessionStorage(session) {
  wx.setStorageSync(SESSION_KEY, session);
}

function clearSessionStorage() {
  wx.removeStorageSync(SESSION_KEY);
}

module.exports = {
  getSessionStorage,
  setSessionStorage,
  clearSessionStorage
};
