package io.github.matijaradovic.spona.mail;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.JavaMailSenderImpl;

import io.github.matijaradovic.spona.support.ContainersConfig;
import jakarta.mail.internet.MimeMessage;

@SpringBootTest
@Import({ ContainersConfig.class, MailNonBlockingTest.SlowSender.class })
class MailNonBlockingTest {

    private static final Duration SEND_TAKES = Duration.ofSeconds(2);
    private static final Duration CALLER_BUDGET = Duration.ofMillis(500);

    @Autowired
    private MailService mail;

    @Test
    void aSlowSendDoesNotHoldTheCaller() throws Exception {
        long start = System.nanoTime();
        mail.sendVerification("ana@example.com", "Ana", "http://localhost:5173/verify?token=t");
        Duration returnedIn = Duration.ofNanos(System.nanoTime() - start);

        assertThat(returnedIn).isLessThan(CALLER_BUDGET);
        assertThat(SlowSender.sent.await(10, TimeUnit.SECONDS))
                .as("the send still happens, just not on this thread")
                .isTrue();
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class SlowSender {

        static final CountDownLatch sent = new CountDownLatch(1);

        @Bean
        JavaMailSender javaMailSender() {
            return new JavaMailSenderImpl() {

                @Override
                public void send(MimeMessage message) {
                    send(new MimeMessage[] { message });
                }

                @Override
                public void send(MimeMessage... messages) {
                    try {
                        Thread.sleep(SEND_TAKES);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        return;
                    }
                    sent.countDown();
                }
            };
        }
    }
}
