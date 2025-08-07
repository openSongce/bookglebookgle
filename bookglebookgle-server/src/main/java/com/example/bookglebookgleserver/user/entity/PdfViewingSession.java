package com.example.bookglebookgleserver.user.entity;

import com.example.bookglebookgleserver.group.entity.Group;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.ManyToOne;
import lombok.*;

import java.time.LocalDateTime;


@Entity
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Getter
@Setter
public class PdfViewingSession {
    @Id
    @GeneratedValue
    private Long id;

    @ManyToOne
    private User user;

    @ManyToOne
    private Group group;

    private LocalDateTime enterTime;
    private LocalDateTime exitTime;

    private Long durationSeconds;

}
