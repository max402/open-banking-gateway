package de.adorsys.opba.core.protocol.domain.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import javax.persistence.Entity;
import javax.persistence.EnumType;
import javax.persistence.Enumerated;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import javax.persistence.JoinColumn;
import javax.persistence.ManyToOne;
import javax.persistence.SequenceGenerator;

// TODO - do we need sequence?
@Entity
@Getter
@Setter
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class BankProtocol {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "bank_protocol_id_generator")
    @SequenceGenerator(name = "bank_protocol_id_generator", sequenceName = "bank_protocol_id_sequence")
    private Long id;

    @ManyToOne
    @JoinColumn(name = "configuration_id", nullable = false)
    private BankConfiguration configuration;

    @Enumerated(EnumType.STRING)
    private ProtocolAction action;

    private String processName;
}
