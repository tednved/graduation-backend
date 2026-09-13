package com.graduation.backend.review.query;

/** 评分分布的一档：某个分数与它对应的可见评价条数。 */
public class RatingCountRow {

    private Integer rating;
    private Integer total;

    public Integer getRating() {
        return rating;
    }

    public void setRating(Integer rating) {
        this.rating = rating;
    }

    public Integer getTotal() {
        return total;
    }

    public void setTotal(Integer total) {
        this.total = total;
    }
}
