package com.graduation.backend.review.query;

import com.baomidou.mybatisplus.core.metadata.IPage;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 评价列表与信用摘要查询（MyBatis-Plus 物理分页）。
 *
 * <p>只读 {@code VISIBLE} 评价：状态过滤写死在 SQL 里，调用方无法查到他本不该看到的内容。
 */
@Mapper
public interface ReviewQueryMapper {

    /** 用户收到的评价，按创建时间倒序；{@code rating} 为 {@code null} 表示不按分数筛选。 */
    IPage<ReviewRow> selectUserReviews(
            IPage<ReviewRow> page,
            @Param("revieweeId") Long revieweeId,
            @Param("rating") Integer rating);

    /** 1~5 分各档的可见评价条数；没有评价的分数不出现在结果里，由服务层补 0。 */
    List<RatingCountRow> countByRating(@Param("revieweeId") Long revieweeId);
}
