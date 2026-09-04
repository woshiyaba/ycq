const util = require('./util.js');
const statuses = {
  PENDING: '等待付款',
  PAID: '等待交付',
  SHIPPED: '等待收货',
  COMPLETED: '交易完成',
  CANCELLED: '已取消'
};
const deliveryNames = {
  EXPRESS: '快递邮寄',
  MEET: '同城面交',
  SELF_TAKE: '同城自提'
};

function decorate(order) {
  if (order) {
    ['createdAt', 'paidAt', 'shippedAt', 'completedAt'].forEach(key => {
      if (order[key]) order[key] = util.displayTime(order[key]);
    });
    if (order.review) order.review.createdAt = util.displayTime(order.review.createdAt);
    order.statusText = statuses[order.status] || order.status;
    order.deliveryText = deliveryNames[order.deliveryMethod] || order.deliveryMethod;
  }
  return order;
}
module.exports = {
  statuses,
  deliveryNames,
  decorate
};
