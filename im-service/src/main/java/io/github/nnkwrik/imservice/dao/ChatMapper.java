package io.github.nnkwrik.imservice.dao;

import io.github.nnkwrik.imservice.model.po.Chat;
import org.apache.ibatis.annotations.*;

import java.util.List;

/**
 * @author nnkwrik
 * @date 18/12/05 21:54
 */
@Mapper
public interface ChatMapper {

    @Insert("insert into chat (u1, u2, goods_id, post_id, show_to_u1, show_to_u2)\n" +
            "values (#{u1}, #{u2}, #{goodsId}, #{postId}, #{showToU1}, #{showToU2}) " +
            "ON DUPLICATE KEY UPDATE id=LAST_INSERT_ID(id), " +
            "show_to_u1=(show_to_u1 OR VALUES(show_to_u1)), show_to_u2=(show_to_u2 OR VALUES(show_to_u2))")
    @SelectKey(resultType = Integer.class, before = false, keyProperty = "id", statement = "SELECT LAST_INSERT_ID()")
    void addChat(Chat chat);

    @Update("update chat\n" +
            "set show_to_u1 = true , show_to_u2 = true\n" +
            "where id = #{chat_id}")
    void showToBoth(@Param("chat_id") int chatId);

    @Select("select id,u1,u2,goods_id,post_id from chat where id = #{id}")
    Chat getChatById(@Param("id") int id);

    @Select("select id\n" +
            "from chat where (u1 = #{user_id} and show_to_u1 = true) or (u2 = #{user_id} and show_to_u2 = true)")
    List<Integer> getChatIdsByUser(@Param("user_id") String userId);

    @Select("select id from chat where u1 = #{u1} and u2 = #{u2} and goods_id = #{goodsId} and post_id = #{postId}")
    Integer getChatIdByChat(Chat chat);

}
