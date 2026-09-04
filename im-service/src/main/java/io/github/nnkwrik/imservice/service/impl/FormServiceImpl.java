package io.github.nnkwrik.imservice.service.impl;

import com.github.pagehelper.PageHelper;
import fangxianyu.innerApi.goods.GoodsClient;
import fangxianyu.innerApi.goods.GoodsClientHandler;
import fangxianyu.innerApi.user.UserClientHandler;
import io.github.nnkwrik.common.dto.SimpleGoods;
import io.github.nnkwrik.common.dto.SimpleContent;
import io.github.nnkwrik.common.dto.Response;
import io.github.nnkwrik.common.dto.SimpleUser;
import io.github.nnkwrik.imservice.dao.ChatMapper;
import io.github.nnkwrik.imservice.dao.HistoryMapper;
import io.github.nnkwrik.imservice.model.po.Chat;
import io.github.nnkwrik.imservice.model.po.History;
import io.github.nnkwrik.imservice.model.vo.ChatForm;
import io.github.nnkwrik.imservice.model.vo.WsMessage;
import io.github.nnkwrik.imservice.redis.RedisClient;
import io.github.nnkwrik.imservice.service.FormService;
import io.github.nnkwrik.imservice.service.ChatAccess;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.ObjectUtils;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * @author nnkwrik
 * @date 18/12/07 22:39
 */
@Service
@Slf4j
public class FormServiceImpl implements FormService {


    @Autowired
    private HistoryMapper historyMapper;

    @Autowired
    private ChatMapper chatMapper;

    @Autowired
    private UserClientHandler userClientHandler;

    @Autowired
    private GoodsClientHandler goodsClientHandler;

    @Autowired
    private GoodsClient goodsClient;

    @Autowired
    private RedisClient redisClient;


    @Override
    public ChatForm showForm(int chatId, String userId, int size, Date offsetTime) {
        if (size < 1 || size > 50) throw new IllegalArgumentException("每页消息数量需为 1 至 50");
        ChatForm vo = new ChatForm();
        Chat chat = chatMapper.getChatById(chatId);
        ChatAccess.requireMember(chat, userId);
        if (chat.getPostId() != null && chat.getPostId() > 0) {
            Response<Map<Integer, SimpleContent>> response = goodsClient.getSimpleContentList(Collections.singletonList(chat.getPostId()));
            if (response != null && response.getErrno() == 0 && response.getData() != null) {
                vo.setContent(response.getData().get(chat.getPostId()));
            }
        } else {
            SimpleGoods goods = goodsClientHandler.getSimpleGoods(chat.getGoodsId());
            vo.setGoods(goods == null ? SimpleGoods.unknownGoods() : goods);
        }
        vo.setIsU1(userId.equals(chat.getU1()));
        SimpleUser other = userClientHandler.getSimpleUser(vo.getIsU1() ? chat.getU2() : chat.getU1());
        vo.setOtherSide(other == null ? SimpleUser.unknownUser() : other);
        final Date cursor = offsetTime == null ? new Date() : offsetTime;
        synchronized (redisClient) {
            // Read incoming messages from SQL after flushing; only the remaining outgoing queue is merged.
            flushUnread(chatId, userId);
            PageHelper.offsetPage(0, size);
            List<History> stored = historyMapper.getChatHistory(chatId, cursor);
            List<History> all = stored == null ? new ArrayList<>() : new ArrayList<>(stored);
            if (all.size() >= size) {
                long boundary = second(all.get(all.size() - 1).getSendTime());
                all.removeIf(item -> second(item.getSendTime()) == boundary);
                all.addAll(historyMapper.getChatHistoryAtSecond(chatId, new Date(boundary), new Date(Math.min(boundary + 1000, cursor.getTime()))));
            }
            List<WsMessage> remaining = redisClient.get(chatId + "");
            all.addAll(WsListToHisList(remaining));
            List<History> history = all.stream()
                    .filter(item -> item.getSendTime() != null && item.getSendTime().before(cursor))
                    .sorted(Comparator.comparing(History::getSendTime)
                            .thenComparing(item -> item.getId() == null ? Integer.MAX_VALUE : item.getId()))
                    .collect(Collectors.toList());
            // ponytail: a page includes the entire boundary second; use an ID cursor if one-second bursts become too large.
            if (history.size() > size) {
                long boundary = second(history.get(history.size() - size).getSendTime());
                history.removeIf(item -> item.getSendTime().getTime() < boundary);
            }
            vo.setHistoryList(history);
            if (!history.isEmpty()) vo.setOffsetTime(new Date(second(history.get(0).getSendTime())));
        }
        return vo;
    }

    private long second(Date time) {
        return Math.floorDiv(time.getTime(), 1000) * 1000;
    }

    /**
     * 返回自己或对方未读的消息,获取并把自己未读的设为已读
     *
     * @param chatId
     * @param userId
     * @return
     */
    @Override
    public List<History> flushUnread(int chatId, String userId) {
        ChatAccess.requireMember(chatMapper.getChatById(chatId), userId);
        synchronized (redisClient) {
            List<WsMessage> unread = redisClient.get(chatId + "");
            if (ObjectUtils.isEmpty(unread)) return Collections.emptyList();
            List<WsMessage> mine = unread.stream()
                    .filter(message -> userId.equals(message.getReceiverId()))
                    .collect(Collectors.toList());
            if (!mine.isEmpty()) {
                historyMapper.addHistoryList(WsListToHisList(mine));
                List<WsMessage> remaining = new ArrayList<>(unread);
                remaining.removeAll(mine);
                redisClient.set(chatId + "", remaining);
            }
            return WsListToHisList(unread);
        }
    }


    private List<History> WsListToHisList(List<WsMessage> wsMessageList) {
        if (ObjectUtils.isEmpty(wsMessageList)) return Collections.emptyList();
        List<History> historyList = new ArrayList<>();
        wsMessageList.forEach(msg -> {
            History history = new History();
            BeanUtils.copyProperties(msg, history);
            history.setU1ToU2(msg.getSenderId().compareTo(msg.getReceiverId()) < 0);
            historyList.add(history);
        });
        return historyList;
    }


}
