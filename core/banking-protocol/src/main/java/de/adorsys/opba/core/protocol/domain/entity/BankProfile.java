package de.adorsys.opba.core.protocol.domain.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import javax.persistence.Entity;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import javax.persistence.MapsId;
import javax.persistence.OneToOne;
import javax.persistence.SequenceGenerator;

@Entity
@Getter
@Setter
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class BankProfile {
    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "bank_profile_id_generator")
    @SequenceGenerator(name = "bank_profile_id_generator", sequenceName = "bank_profile_id_sequence")
    private Long id;

    @OneToOne
    @MapsId
    private Bank bank;

    private String url;
    private String adapterId;
    private String idpUrl;
    private String scaApproaches;
}
