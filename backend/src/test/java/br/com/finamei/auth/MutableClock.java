package br.com.finamei.auth;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;

/** Relógio de teste que avança sob demanda, para exercitar o bloqueio sem esperar. */
class MutableClock extends Clock {

	private Instant instant;

	MutableClock(Instant instant) {
		this.instant = instant;
	}

	void advance(Duration duration) {
		instant = instant.plus(duration);
	}

	@Override
	public ZoneId getZone() {
		return ZoneOffset.UTC;
	}

	@Override
	public Clock withZone(ZoneId zone) {
		return this;
	}

	@Override
	public Instant instant() {
		return instant;
	}
}
