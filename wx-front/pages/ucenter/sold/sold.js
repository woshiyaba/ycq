Page({
  onLoad() {
    wx.redirectTo({
      url: '/pages/orders/list/list?role=seller'
    });
  }
});
