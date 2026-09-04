package io.github.nnkwrik.userservice.dao;

import io.github.nnkwrik.common.dto.UserProfile;
import org.apache.ibatis.annotations.*;

@Mapper
public interface ProfileMapper {
    @Select("SELECT u.open_id, COALESCE(p.nick_name,u.nick_name) nick_name, " +
            "COALESCE(p.avatar_url,u.avatar_url) avatar_url,u.register_time, " +
            "COALESCE(p.gender,u.gender,0) gender,COALESCE(p.bio,'') bio,COALESCE(p.region,u.city,'') region " +
            "FROM user u LEFT JOIN user_profile p ON p.open_id=u.open_id WHERE u.open_id=#{id}")
    UserProfile get(@Param("id") String id);

    @Insert("INSERT INTO user_profile(open_id,nick_name,avatar_url,bio,gender,region) " +
            "VALUES(#{openId},#{nickName},#{avatarUrl},#{bio},#{gender},#{region}) " +
            "ON DUPLICATE KEY UPDATE nick_name=VALUES(nick_name),avatar_url=VALUES(avatar_url)," +
            "bio=VALUES(bio),gender=VALUES(gender),region=VALUES(region)")
    void save(UserProfile profile);
}
