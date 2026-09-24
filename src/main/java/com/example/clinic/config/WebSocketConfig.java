package com.example.clinic.config;

import com.example.clinic.domain.User;
import com.example.clinic.repository.DoctorRepository;
import com.example.clinic.repository.PatientRepository;
import com.example.clinic.repository.UserRepository;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.config.ChannelRegistration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;

import java.security.Principal;

@Configuration
@EnableWebSocketMessageBroker
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

    private final UserRepository userRepository;
    private final PatientRepository patientRepository;
    private final DoctorRepository doctorRepository;

    public WebSocketConfig(UserRepository userRepository,
                           PatientRepository patientRepository,
                           DoctorRepository doctorRepository) {
        this.userRepository = userRepository;
        this.patientRepository = patientRepository;
        this.doctorRepository = doctorRepository;
    }

    @Override
    public void configureMessageBroker(MessageBrokerRegistry config) {
        config.enableSimpleBroker("/topic");
        config.setApplicationDestinationPrefixes("/app");
    }

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        registry.addEndpoint("/ws").withSockJS();
    }

    @Override
    public void configureClientInboundChannel(ChannelRegistration registration) {
        registration.interceptors(new ChannelInterceptor() {
            @Override
            public Message<?> preSend(Message<?> message, MessageChannel channel) {
                StompHeaderAccessor accessor = StompHeaderAccessor.wrap(message);
                StompCommand command = accessor.getCommand();

                if (command == StompCommand.CONNECT || command == StompCommand.SUBSCRIBE) {
                    Principal principal = accessor.getUser();
                    if (principal == null) {
                        throw new AccessDeniedException("Autentificarea WebSocket este obligatorie.");
                    }

                    if (command == StompCommand.SUBSCRIBE) {
                        authorizeSubscription(principal, accessor.getDestination());
                    }
                }

                return message;
            }

            private void authorizeSubscription(Principal principal, String destination) {
                if (destination == null) {
                    throw new AccessDeniedException("Destinatia WebSocket este obligatorie.");
                }

                if (destination.startsWith("/topic/patient/")) {
                    authorizePatientSubscription(principal, destination);
                    return;
                }

                if (destination.startsWith("/topic/doctor/")) {
                    authorizeDoctorSubscription(principal, destination);
                    return;
                }

                throw new AccessDeniedException("Destinatia WebSocket nu este permisa.");
            }

            private void authorizePatientSubscription(Principal principal, String destination) {
                Long targetId = extractTargetId(destination, "/topic/patient/");
                User user = findUser(principal);

                if (!"PATIENT".equals(user.getRole())) {
                    throw new AccessDeniedException("Nu aveți acces la notificările pacientului.");
                }

                Long ownId = patientRepository.findByUser(user)
                        .orElseThrow(() -> new AccessDeniedException("Profilul pacientului nu există."))
                        .getId();

                if (!ownId.equals(targetId)) {
                    throw new AccessDeniedException("Nu aveți acces la notificările acestui pacient.");
                }
            }

            private void authorizeDoctorSubscription(Principal principal, String destination) {
                Long targetId = extractTargetId(destination, "/topic/doctor/");
                User user = findUser(principal);

                if (!"DOCTOR".equals(user.getRole())) {
                    throw new AccessDeniedException("Nu aveți acces la notificările doctorului.");
                }

                Long ownId = doctorRepository.findByUser(user)
                        .orElseThrow(() -> new AccessDeniedException("Profilul doctorului nu există."))
                        .getId();

                if (!ownId.equals(targetId)) {
                    throw new AccessDeniedException("Nu aveți acces la notificările acestui doctor.");
                }
            }

            private User findUser(Principal principal) {
                return userRepository.findByEmail(principal.getName())
                        .orElseThrow(() -> new AccessDeniedException("Utilizatorul WebSocket nu există."));
            }

            private Long extractTargetId(String destination, String prefix) {
                String suffix = destination.substring(prefix.length());
                String idPart = suffix.endsWith("/notifications")
                        ? suffix.substring(0, suffix.length() - "/notifications".length())
                        : suffix;

                try {
                    return Long.valueOf(idPart);
                } catch (NumberFormatException ex) {
                    throw new AccessDeniedException("Destinatia WebSocket este invalida.");
                }
            }
        });
    }
}
