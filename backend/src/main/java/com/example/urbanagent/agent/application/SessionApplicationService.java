package com.example.urbanagent.agent.application;

import com.example.urbanagent.agent.application.dto.MessageView;
import com.example.urbanagent.agent.application.dto.SessionView;
import com.example.urbanagent.agent.domain.AgentSession;
import com.example.urbanagent.audit.application.AuditLogService;
import com.example.urbanagent.agent.repository.AgentMessageRepository;
import com.example.urbanagent.agent.repository.AgentSessionRepository;
import com.example.urbanagent.common.error.BusinessException;
import com.example.urbanagent.common.error.ErrorCode;
import com.example.urbanagent.iam.domain.UserContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class SessionApplicationService {

    private final AgentSessionRepository sessionRepository;
    private final AgentMessageRepository messageRepository;
    private final MessageViewMapper messageViewMapper;
    private final AuditLogService auditLogService;

    public SessionApplicationService(AgentSessionRepository sessionRepository,
                                     AgentMessageRepository messageRepository,
                                     MessageViewMapper messageViewMapper,
                                     AuditLogService auditLogService) {
        this.sessionRepository = sessionRepository;
        this.messageRepository = messageRepository;
        this.messageViewMapper = messageViewMapper;
        this.auditLogService = auditLogService;
    }

    @Transactional
    public SessionView createSession(String title) {
        AgentSession session = sessionRepository.save(new AgentSession(UserContextHolder.get().userId(), title));
        auditLogService.recordAgentSessionCreated(session);
        return SessionView.from(session, List.of());
    }

    @Transactional(readOnly = true)
    public SessionView getSession(String sessionId) {
        AgentSession session = sessionRepository.findById(sessionId)
                .orElseThrow(() -> new BusinessException(ErrorCode.SESSION_NOT_FOUND));
        List<MessageView> messages = messageRepository.findBySessionIdOrderByCreatedAtAsc(sessionId)
                .stream()
                .map(messageViewMapper::toView)
                .toList();
        return SessionView.from(session, messages);
    }

    @Transactional(readOnly = true)
    public List<SessionView> listSessions() {
        return sessionRepository.findTop20ByUserIdOrderByCreatedAtDesc(UserContextHolder.get().userId())
                .stream()
                .map(session -> SessionView.from(session, List.of()))
                .toList();
    }
}
