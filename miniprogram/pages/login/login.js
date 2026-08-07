const { loginWx } = require("../../utils/api");
const { persistSession } = require("../../utils/auth");

function getErrorMessage(error, fallbackMessage) {
  if (!error) {
    return fallbackMessage;
  }
  if (typeof error === "string") {
    return error;
  }
  if (error.message) {
    return error.message;
  }
  if (error.errMsg) {
    return error.errMsg;
  }
  return fallbackMessage;
}

function requestUserProfile() {
  return new Promise((resolve, reject) => {
    wx.getUserProfile({
      desc: "用于登录并展示头像昵称",
      success(res) {
        resolve(res.userInfo || {});
      },
      fail(error) {
        reject(error);
      }
    });
  });
}

Page({
  data: {
    loading: false,
    errorMessage: "",
    quickEntries: [
      { label: "智能问答", hint: "直接生成行程" },
      { label: "热门目的地", hint: "查看出行灵感" },
      { label: "预算助手", hint: "快速拆分花费" },
      { label: "历史方案", hint: "回看已保存会话" }
    ]
  },

  onShow() {
    const app = getApp();
    if (app.globalData.session) {
      wx.reLaunch({
        url: "/pages/chat/chat"
      });
    }
  },

  async submitLogin() {
    if (this.data.loading) {
      return;
    }

    this.setData({
      loading: true,
      errorMessage: ""
    });

    try {
      const userInfo = await requestUserProfile();
      const loginResult = await new Promise((resolve, reject) => {
        wx.login({
          success: resolve,
          fail: reject
        });
      });

      const session = await loginWx({
        code: loginResult.code,
        nickname: userInfo.nickName || "微信用户",
        avatarUrl: userInfo.avatarUrl || ""
      });

      persistSession(session);
      wx.reLaunch({
        url: "/pages/chat/chat"
      });
    } catch (error) {
      this.setData({
        errorMessage: getErrorMessage(error, "微信授权登录失败")
      });
    } finally {
      this.setData({
        loading: false
      });
    }
  }
});
