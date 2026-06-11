package com.university.microservices.broker.eventchain.step;

import com.university.microservices.broker.eventchain.AbstractEventStep;
import com.university.microservices.broker.eventchain.EventContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class SendEmailEventStep extends AbstractEventStep {

    private final JavaMailSender mailSender;

    @Override
    protected void process(EventContext context) throws Exception {
        log.info("Sending email for topic: {}", context.getTopic());
        try {
            SimpleMailMessage message = new SimpleMailMessage();
            message.setFrom("noreply@university.com");
            String recipient = "cliente@university.com";
            if (context.getData().containsKey("usuarioId")) {
                String usuarioId = context.getData().get("usuarioId").toString();
                if (usuarioId.contains("@")) {
                    recipient = usuarioId;
                } else {
                    recipient = usuarioId + "@university.com";
                }
            }
            message.setTo(recipient);
            
            String subject = "Notificación de Sistema";
            String text = "Evento recibido: " + context.getTopic();

            if ("order_status_changed_events".equals(context.getTopic())) {
                subject = "Cambio de Estado en su Orden";
                text = "Su orden " + context.getData().get("id") + " ha cambiado a: " + context.getData().get("status");
            } else if ("payment_received_events".equals(context.getTopic())) {
                subject = "Pago Recibido";
                text = "Hemos recibido su pago por la orden: " + context.getData().get("ordenId");
            }

            message.setSubject(subject);
            message.setText(text);
            mailSender.send(message);
            log.info("Email sent successfully");
        } catch (Exception e) {
            log.error("Error sending email in event chain", e);
        }
    }
}
