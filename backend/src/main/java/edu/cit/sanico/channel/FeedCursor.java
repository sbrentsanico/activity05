package edu.cit.sanico.channel;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "feed_cursor")
class FeedCursor {

    @Id
    @Column(name = "feed_id")
    private String feedId;

    @Column(name = "last_cursor")
    private String lastCursor;

    public FeedCursor() {}

    public FeedCursor(String feedId, String lastCursor) {
        this.feedId = feedId;
        this.lastCursor = lastCursor;
    }

    public String getFeedId() { return feedId; }
    public void setFeedId(String feedId) { this.feedId = feedId; }

    public String getLastCursor() { return lastCursor; }
    public void setLastCursor(String lastCursor) { this.lastCursor = lastCursor; }
}
