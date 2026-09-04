package io.github.nnkwrik.imservice.service.impl;

import fangxianyu.innerApi.goods.GoodsClientHandler;
import fangxianyu.innerApi.goods.GoodsClient;
import fangxianyu.innerApi.user.UserClientHandler;
import io.github.nnkwrik.common.dto.SimpleGoods;
import io.github.nnkwrik.common.dto.SimpleContent;
import io.github.nnkwrik.common.dto.Response;
import io.github.nnkwrik.common.dto.SimpleUser;
import io.github.nnkwrik.common.util.ListUtil;
import io.github.nnkwrik.imservice.dao.ChatMapper;
import io.github.nnkwrik.imservice.dao.HistoryMapper;
import io.github.nnkwrik.imservice.model.po.History;
import io.github.nnkwrik.imservice.model.po.HistoryExample;
import io.github.nnkwrik.imservice.model.vo.ChatIndex;
import io.github.nnkwrik.imservice.model.vo.ChatIndexEle;
import io.github.nnkwrik.imservice.model.vo.WsMessage;
import io.github.nnkwrik.imservice.redis.RedisClient;
import io.github.nnkwrik.imservice.service.IndexService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.ObjectUtils;

import java.util.*;
import java.util.stream.Collectors;

/**
 * @author nnkwrik
 * @date 18/12/07 16:32
 */
@Service
@Slf4j
public class IndexServiceImpl implements IndexService {

    @Autowired
    private RedisClient redisClient;

    @Autowired
    private HistoryMapper historyMapper;

    @Autowired
    private UserClientHandler userClientHandler;

    @Autowired
    private GoodsClientHandler goodsClientHandler;

    @Autowired
    private GoodsClient goodsClient;

    @Autowired
    private ChatMapper chatMapper;


    @Override
    public ChatIndex showIndex(String currentUser, int size, Date offsetTime) {
        if (size < 1 || size > 50) throw new IllegalArgumentException("每页会话数量需为 1 至 50");
        if (offsetTime == null) offsetTime = new Date();
        //用户参与的所有chatId
        List<Integer> chatIds = chatMapper.getChatIdsByUser(currentUser);

        List<ChatIndexEle> unread;
        List<ChatIndexEle> read;
        synchronized (redisClient) {
            List<List<WsMessage>> unreadMessage = chatIds.isEmpty() ? Collections.emptyList() : redisClient.multiGet(
                    chatIds.stream().map(String::valueOf).collect(Collectors.toList()));
            unread = getDisplayUnread(currentUser, unreadMessage);
            read = getDisplayRead(currentUser);
        }

        //排序后删除超出size的
        List<ChatIndexEle> limited = sortAndLimit(unread, read, size, offsetTime);

        //添加用户和商品信息
        List<ChatIndexEle> chats = setGoodsAndUser(limited);

        ChatIndex vo = new ChatIndex();
        vo.setChats(chats);
        if (!ObjectUtils.isEmpty(chats)) {
            vo.setOffsetTime(ListUtil.getLast(chats).getLastChat().getSendTime());
        }

        return vo;
    }


    private List<ChatIndexEle> getDisplayUnread(String currentUserId, List<List<WsMessage>> unreadMessage) {



        List<ChatIndexEle> unread = unreadMessage.stream()
                .filter(msgList -> !ObjectUtils.isEmpty(msgList))
                .map(msgList -> {

                    WsMessage lastMsg = ListUtil.getLast(msgList);

                    ChatIndexEle indexEle = new ChatIndexEle();
                    indexEle.setGoodsId(lastMsg.getGoodsId());
                    indexEle.setPostId(lastMsg.getPostId());
                    indexEle.setUnreadCount((int) msgList.stream().filter(msg -> currentUserId.equals(msg.getReceiverId())).count());
                    if (lastMsg.getSenderId().equals(currentUserId)) {
                        indexEle.setUserId(lastMsg.getReceiverId());
                    } else {
                        indexEle.setUserId(lastMsg.getSenderId());
                    }

                    //设置最后一条信息
                    History lastHistory = new History();
                    BeanUtils.copyProperties(lastMsg, lastHistory);
                    lastHistory.setU1ToU2(lastMsg.getSenderId().compareTo(lastMsg.getReceiverId()) < 0);
                    indexEle.setLastChat(lastHistory);

                    return indexEle;
                }).collect(Collectors.toList());

        return unread;

    }

    private List<ChatIndexEle> getDisplayRead(String currentUser) {
        // ponytail: merge one SQL row per visible chat with the existing Redis queues;
        // materialize each chat's latest message if the number of conversations makes this too expensive.
        List<HistoryExample> readHistory = historyMapper.getLastReadChat(Collections.emptyList(), currentUser, null);

        List<ChatIndexEle> read = readHistory.stream()
                .map(history -> {
                    ChatIndexEle indexEle = new ChatIndexEle();
                    indexEle.setGoodsId(history.getGoodsId());
                    indexEle.setPostId(history.getPostId());
                    if (currentUser.equals(history.getU1())) {
                        indexEle.setUserId(history.getU2());
                    } else {
                        indexEle.setUserId(history.getU1());
                    }

                    //设置未读数
                    indexEle.setUnreadCount(0);

                    //设置最后一条信息
                    History lastChat = new History();
                    BeanUtils.copyProperties(history, lastChat);
                    indexEle.setLastChat(lastChat);

                    return indexEle;
                }).collect(Collectors.toList());

        return read;

    }


    private List<ChatIndexEle> sortAndLimit(List<ChatIndexEle> unread, List<ChatIndexEle> read, int size, Date offsetTime) {
        Map<Integer, ChatIndexEle> byChat = new HashMap<>();
        unread.forEach(item -> byChat.put(item.getLastChat().getChatId(), item));
        for (ChatIndexEle item : read) {
            ChatIndexEle queued = byChat.get(item.getLastChat().getChatId());
            if (queued == null || item.getLastChat().getSendTime().after(queued.getLastChat().getSendTime())) {
                if (queued != null) item.setUnreadCount(queued.getUnreadCount());
                byChat.put(item.getLastChat().getChatId(), item);
            }
        }
        List<ChatIndexEle> limited = byChat.values().stream()
                .filter(item -> item.getLastChat().getSendTime().before(offsetTime))
                .sorted((a, b) -> b.getLastChat().getSendTime().compareTo(a.getLastChat().getSendTime()))
                .limit(size)
                .collect(Collectors.toList());
        return limited;

    }

    private List<ChatIndexEle> setGoodsAndUser(List<ChatIndexEle> eleList) {
        Set<Integer> goodsIds = new HashSet<>();
        Set<Integer> postIds = new HashSet<>();
        Set<String> userIds = new HashSet<>();

        eleList.stream().forEach(ele -> {
            if (ele.getPostId() != null && ele.getPostId() > 0) postIds.add(ele.getPostId());
            else if (ele.getGoodsId() != null && ele.getGoodsId() > 0) goodsIds.add(ele.getGoodsId());
            userIds.add(ele.getUserId());
        });

        //去商品服务查商品图片
        Map<Integer, SimpleGoods> simpleGoodsMap
                = goodsClientHandler.getSimpleGoodsList(new ArrayList<>(goodsIds));

        Map<Integer, SimpleContent> contents = new HashMap<>();
        if (!postIds.isEmpty()) {
            Response<Map<Integer, SimpleContent>> response = goodsClient.getSimpleContentList(new ArrayList<>(postIds));
            if (response != null && response.getErrno() == 0 && response.getData() != null) contents.putAll(response.getData());
        }

        //去用户服务查用户名字头像
        Map<String, SimpleUser> simpleUserMap
                = userClientHandler.getSimpleUserList(new ArrayList<>(userIds));


        eleList.stream().forEach(ele -> {

            String userId = ele.getUserId();
            SimpleUser simpleUser = simpleUserMap == null ? null : simpleUserMap.get(userId);
            ele.setOtherSide(simpleUser == null ? SimpleUser.unknownUser() : simpleUser);
            if (ele.getPostId() != null && ele.getPostId() > 0) {
                ele.setContent(contents.get(ele.getPostId()));
            } else {
                SimpleGoods simpleGoods = simpleGoodsMap == null ? null : simpleGoodsMap.get(ele.getGoodsId());
                ele.setGoods(simpleGoods == null ? SimpleGoods.unknownGoods() : simpleGoods);
            }
        });


        return eleList;
    }

}
