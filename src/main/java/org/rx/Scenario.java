package org.rx;

import java.util.Objects;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.Supplier;

public class Scenario {

	public static class Static {

		public static Scenario when(BooleanSupplier condition) {
			return new Scenario(condition.getAsBoolean());
		}

		public static Scenario when(Boolean condition) {
			return new Scenario(condition);
		}

		public static <T> Step<T> step(Supplier<T> action) {
			return new Scenario().step(action);
		}

		public static Step<Void> step(Runnable action) {
			return new Scenario().step(action);
		}

		public static void raise(RuntimeException exception) {
			throw exception;
		}
	}

	private final boolean isExecutable;

	public Scenario(boolean isExecutable) {
		this.isExecutable = isExecutable;
	}

	public Scenario() {
		this(true);
	}

	public <T> Step<T> step(Supplier<T> action) {
		return new Step<T>(isExecutable).action(action);
	}

	public <T> Step<T> step(Supplier<T> action, Supplier<? extends RuntimeException> exception) {
		return new Step<T>(isExecutable).action(action, exception);
	}

	public Step<Void> step(Runnable action) {
		return new Step<Void>(isExecutable).action(action);
	}

	public static class Step<T> {

		private T result;
		private boolean isExecutable;
		private RuntimeException exception;

		public Step(boolean isExecutable) {
			this.isExecutable = isExecutable;
		}

		/**
		 * Представляет основное действие шага,
		 * сохраняет выброшенное исключение для дальнейшей обработки
		 *
		 * @param supplier - действие
		 */
		public Step<T> action(Supplier<T> supplier) {
			if (!isExecutable) return this;

			try {
				result = supplier.get();
			} catch (RuntimeException e) {
				exception = e;
			}

			return this;
		}

		/**
		 * Представляет действие, которое не возвращает результат
		 * @param runnable - действие
		 * @return
		 */
		public Step<T> action(Runnable runnable) {
			if (!isExecutable) return this;

			try {
				runnable.run();
			} catch (RuntimeException e) {
				exception = e;
			}

			return this;
		}

		/**
		 * Представляет основное действие шага,
		 * заменяет исключение, брошенное действием на предоставленное
		 *
		 * @param supplier  - действие
		 * @param exception - исключение, которое будет выброшено
		 */
		public Step<T> action(Supplier<T> supplier, Supplier<? extends RuntimeException> exception) {
			if (!isExecutable) return this;

			try {
				result = supplier.get();
			} catch (Exception e) {
				throw exception.get();
			}

			return this;
		}

		/**
		 * Восстановить шаг, если на этапе основного действия возникло исключение
		 *
		 * @param recover - функция восставновления шага
		 */
		public Step<T> recover(Function<RuntimeException, T> recover) {
			if (!isExecutable) return this;

			if (exception != null) {
				result = recover.apply(exception);
			}

			return this;
		}

		/**
		 * Выполняет валидацию результата шага,
		 * бросает исключение, если предикат вернул false
		 *
		 * @param predicate - условие валидации
		 * @param exception - исключение, которое будет выброшено
		 */
		public Step<T> validate(Predicate<T> predicate, Supplier<? extends RuntimeException> exception) {
			if (!isExecutable) return this;

			if (predicate.test(result)) {
				return this;
			}

			throw exception.get();
		}

		/**
		 * Выполняет побочное действие с результатом шага
		 * Позволяет добавить side effect,
		 *
		 * @param consumer - функция, принимающая результат выполнения шага
		 */
		public Step<T> apply(Consumer<T> consumer) {
			if (!isExecutable) return this;

			consumer.accept(result);

			return this;
		}

		/**
		 * Выполняет побочное действие с результатом шага, если предикат вернул true
		 * Позволяет добавить side effect,
		 *
		 * @param consumer - функция, принимающая результат выполнения шага
		 */
		public Step<T> when(Predicate<T> predicate, Consumer<T> consumer) {
			if (!isExecutable) return this;

			if (predicate.test(result)) {
				consumer.accept(result);
			}

			return this;
		}

		/**
		 * Трансформирует результат выполнения шага
		 *
		 * @param transformer - конвертер
		 * @return Step
		 */
		public <R> Step<R> map(Function<T, R> transformer) {
			return new Step<R>(isExecutable).action(() -> transformer.apply(result));
		}

		public T get() {
			if (Objects.isNull(result)) {
				throw new IllegalStateException();
			}

			return result;
		}

		public T orNull() {
			return result;
		}

		public T orElse(T value) {
			if (Objects.isNull(result)) {
				return value;
			}

			return result;
		}

		public T orElse(Supplier<T> supplier) {
			if (Objects.isNull(result)) {
				return supplier.get();
			}

			return result;
		}

		public T orThrow(Supplier<? extends RuntimeException> exception) {
			if (Objects.isNull(result)) {
				throw exception.get();
			}

			return result;
		}
	}

}
