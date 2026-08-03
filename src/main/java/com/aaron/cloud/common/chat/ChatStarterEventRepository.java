package com.aaron.cloud.common.chat;

import com.aaron.cloud.common.chat.entity.ChatStarterEvent;
import com.aaron.cloud.common.chat.mapper.ChatStarterEventMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class ChatStarterEventRepository {

    private final ChatStarterEventMapper mapper;

    public int insert(ChatStarterEvent row) {
        return mapper.insert(row);
    }
}
