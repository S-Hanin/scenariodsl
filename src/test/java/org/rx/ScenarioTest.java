package org.rx;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Objects;
import java.util.logging.Level;
import java.util.logging.Logger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.rx.Scenario.Static.step;
import static org.rx.Scenario.Static.when;

class ScenarioTest {

	Logger log = Logger.getLogger(ScenarioTest.class.getName());

	@Test
	void when_conditionTrueTest() {
		var result = when(() -> true)
			.step(() -> "Hello world")
			.get();

		assertThat(result).isEqualTo("Hello world");
	}

	@Test
	void when_conditionFalseTest() {
		var result = when(() -> false)
			.step(() -> "Hello world")
			.validate(Objects::isNull, IllegalArgumentException::new)
			.orNull();

		assertThat(result).isEqualTo(null);
	}

	@Test
	void action_replaceException() {
		var result = assertThatThrownBy(
			() -> when(() -> true)
				.step(() -> {
						throw new RuntimeException();
					},
					IllegalArgumentException::new));

		result.isInstanceOf(IllegalArgumentException.class);
	}

	@Test
	void validate_throwException_whenFalse() {
		var result = assertThatThrownBy(() -> step(() -> null)
			.validate(
				Objects::nonNull,
				IllegalArgumentException::new));

		result.isInstanceOf(IllegalArgumentException.class);
	}

	@Test
	void validate_returnStep_whenTrue() {
		var step = step(() -> null)
			.validate(Objects::isNull,
				IllegalArgumentException::new);

		assertThat(step).isInstanceOf(Scenario.Step.class);
	}

	@Test
	void apply_mutateResultObject() {
		var step = step(() -> new ArrayList<String>())
			.apply(it -> it.add("Hello"));

		assertThat(step.get()).hasSize(1);
	}

	@Test
	void apply_sideEffect() {
		var result = assertThatThrownBy(() ->
			step(() -> new ArrayList<String>())
				.apply(it -> {
					if (it.size() == 0) {
						throw new RuntimeException();
					}
				})
				.get());

		result.isInstanceOf(RuntimeException.class);

	}

	@Test
	void map_notExecutable() {
		var step = when(() -> false)
			.step(() -> "Hello")
			.map(String::length);

		assertThat(step.orNull()).isEqualTo(null);
	}

	@Test
	void map_convertStepResult() {
		var step = step(() -> "Hello")
			.map(String::length);

		assertThat(step.get()).isEqualTo(5);
	}

	@Test
	void recover_recoverOnError() {
		var step = step(() -> "Hello".endsWith(null))
			.recover(it -> {
				log.log(Level.WARNING, "Got an error", it);
				return true;
			});

		assertThat(step.get()).isTrue();
	}

	@Test
	void nestedStep() {
		var step = step(() -> "Hello")
			.apply(it -> step(() -> it.toUpperCase())
				.apply(val -> log.info(val)));

		assertThat(step.get()).isEqualTo("Hello");
	}

}