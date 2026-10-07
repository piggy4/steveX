package net.minecraft.network.codec;

import com.google.common.base.Suppliers;
import com.mojang.datafixers.util.Function10;
import com.mojang.datafixers.util.Function11;
import com.mojang.datafixers.util.Function12;
import com.mojang.datafixers.util.Function3;
import com.mojang.datafixers.util.Function4;
import com.mojang.datafixers.util.Function5;
import com.mojang.datafixers.util.Function6;
import com.mojang.datafixers.util.Function7;
import com.mojang.datafixers.util.Function8;
import com.mojang.datafixers.util.Function9;
import io.netty.buffer.ByteBuf;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.function.UnaryOperator;

public interface StreamCodec<B, V> extends StreamDecoder<B, V>, StreamEncoder<B, V> {
	static <B, V> StreamCodec<B, V> of(StreamEncoder<B, V> streamEncoder, StreamDecoder<B, V> streamDecoder) {
		return new StreamCodec<B, V>() {
			@Override
			public V decode(B object) {
				return streamDecoder.decode(object);
			}

			@Override
			public void encode(B object, V object2) {
				streamEncoder.encode(object, object2);
			}
		};
	}

	static <B, V> StreamCodec<B, V> ofMember(StreamMemberEncoder<B, V> streamMemberEncoder, StreamDecoder<B, V> streamDecoder) {
		return new StreamCodec<B, V>() {
			@Override
			public V decode(B object) {
				return streamDecoder.decode(object);
			}

			@Override
			public void encode(B object, V object2) {
				streamMemberEncoder.encode(object2, object);
			}
		};
	}

	static <B, V> StreamCodec<B, V> unit(V object) {
		return new StreamCodec<B, V>() {
			@Override
			public V decode(B object) {
				return object;
			}

			@Override
			public void encode(B object, V object2) {
				if (!object2.equals(object)) {
					throw new IllegalStateException("Can't encode '" + object2 + "', expected '" + object + "'");
				}
			}
		};
	}

	default <O> StreamCodec<B, O> apply(StreamCodec.CodecOperation<B, V, O> codecOperation) {
		return codecOperation.apply(this);
	}

	default <O> StreamCodec<B, O> map(Function<? super V, ? extends O> function, Function<? super O, ? extends V> function2) {
		return new StreamCodec<B, O>() {
			@Override
			public O decode(B object) {
				return (O)function.apply(StreamCodec.this.decode(object));
			}

			@Override
			public void encode(B object, O object2) {
				StreamCodec.this.encode(object, (V)function2.apply(object2));
			}
		};
	}

	default <O extends ByteBuf> StreamCodec<O, V> mapStream(Function<O, ? extends B> function) {
		return new StreamCodec<O, V>() {
			public V decode(O byteBuf) {
				B object = (B)function.apply(byteBuf);
				return StreamCodec.this.decode(object);
			}

			public void encode(O byteBuf, V object) {
				B object2 = (B)function.apply(byteBuf);
				StreamCodec.this.encode(object2, object);
			}
		};
	}

	default <U> StreamCodec<B, U> dispatch(Function<? super U, ? extends V> function, Function<? super V, ? extends StreamCodec<? super B, ? extends U>> function2) {
		return new StreamCodec<B, U>() {
			@Override
			public U decode(B object) {
				V object2 = StreamCodec.this.decode(object);
				StreamCodec<? super B, ? extends U> streamCodec = (StreamCodec<? super B, ? extends U>)function2.apply(object2);
				return (U)streamCodec.decode(object);
			}

			@Override
			public void encode(B object, U object2) {
				V object3 = (V)function.apply(object2);
				StreamCodec<B, U> streamCodec = (StreamCodec<B, U>)function2.apply(object3);
				StreamCodec.this.encode(object, object3);
				streamCodec.encode(object, object2);
			}
		};
	}

	static <B, C, T1> StreamCodec<B, C> composite(StreamCodec<? super B, T1> streamCodec, Function<C, T1> function, Function<T1, C> function2) {
		return new StreamCodec<B, C>() {
			@Override
			public C decode(B object) {
				T1 object2 = streamCodec.decode(object);
				return function2.apply(object2);
			}

			@Override
			public void encode(B object, C object2) {
				streamCodec.encode(object, function.apply(object2));
			}
		};
	}

	static <B, C, T1, T2> StreamCodec<B, C> composite(
		StreamCodec<? super B, T1> streamCodec,
		Function<C, T1> function,
		StreamCodec<? super B, T2> streamCodec2,
		Function<C, T2> function2,
		BiFunction<T1, T2, C> biFunction
	) {
		return new StreamCodec<B, C>() {
			@Override
			public C decode(B object) {
				T1 object2 = streamCodec.decode(object);
				T2 object3 = streamCodec2.decode(object);
				return biFunction.apply(object2, object3);
			}

			@Override
			public void encode(B object, C object2) {
				streamCodec.encode(object, function.apply(object2));
				streamCodec2.encode(object, function2.apply(object2));
			}
		};
	}

	static <B, C, T1, T2, T3> StreamCodec<B, C> composite(
		StreamCodec<? super B, T1> streamCodec,
		Function<C, T1> function,
		StreamCodec<? super B, T2> streamCodec2,
		Function<C, T2> function2,
		StreamCodec<? super B, T3> streamCodec3,
		Function<C, T3> function3,
		Function3<T1, T2, T3, C> function32
	) {
		return new StreamCodec<B, C>() {
			@Override
			public C decode(B object) {
				T1 object2 = streamCodec.decode(object);
				T2 object3 = streamCodec2.decode(object);
				T3 object4 = streamCodec3.decode(object);
				return function32.apply(object2, object3, object4);
			}

			@Override
			public void encode(B object, C object2) {
				streamCodec.encode(object, function.apply(object2));
				streamCodec2.encode(object, function2.apply(object2));
				streamCodec3.encode(object, function3.apply(object2));
			}
		};
	}

	static <B, C, T1, T2, T3, T4> StreamCodec<B, C> composite(
		StreamCodec<? super B, T1> streamCodec,
		Function<C, T1> function,
		StreamCodec<? super B, T2> streamCodec2,
		Function<C, T2> function2,
		StreamCodec<? super B, T3> streamCodec3,
		Function<C, T3> function3,
		StreamCodec<? super B, T4> streamCodec4,
		Function<C, T4> function4,
		Function4<T1, T2, T3, T4, C> function42
	) {
		return new StreamCodec<B, C>() {
			@Override
			public C decode(B object) {
				T1 object2 = streamCodec.decode(object);
				T2 object3 = streamCodec2.decode(object);
				T3 object4 = streamCodec3.decode(object);
				T4 object5 = streamCodec4.decode(object);
				return function42.apply(object2, object3, object4, object5);
			}

			@Override
			public void encode(B object, C object2) {
				streamCodec.encode(object, function.apply(object2));
				streamCodec2.encode(object, function2.apply(object2));
				streamCodec3.encode(object, function3.apply(object2));
				streamCodec4.encode(object, function4.apply(object2));
			}
		};
	}

	static <B, C, T1, T2, T3, T4, T5> StreamCodec<B, C> composite(
		StreamCodec<? super B, T1> streamCodec,
		Function<C, T1> function,
		StreamCodec<? super B, T2> streamCodec2,
		Function<C, T2> function2,
		StreamCodec<? super B, T3> streamCodec3,
		Function<C, T3> function3,
		StreamCodec<? super B, T4> streamCodec4,
		Function<C, T4> function4,
		StreamCodec<? super B, T5> streamCodec5,
		Function<C, T5> function5,
		Function5<T1, T2, T3, T4, T5, C> function52
	) {
		return new StreamCodec<B, C>() {
			@Override
			public C decode(B object) {
				T1 object2 = streamCodec.decode(object);
				T2 object3 = streamCodec2.decode(object);
				T3 object4 = streamCodec3.decode(object);
				T4 object5 = streamCodec4.decode(object);
				T5 object6 = streamCodec5.decode(object);
				return function52.apply(object2, object3, object4, object5, object6);
			}

			@Override
			public void encode(B object, C object2) {
				streamCodec.encode(object, function.apply(object2));
				streamCodec2.encode(object, function2.apply(object2));
				streamCodec3.encode(object, function3.apply(object2));
				streamCodec4.encode(object, function4.apply(object2));
				streamCodec5.encode(object, function5.apply(object2));
			}
		};
	}

	static <B, C, T1, T2, T3, T4, T5, T6> StreamCodec<B, C> composite(
		StreamCodec<? super B, T1> streamCodec,
		Function<C, T1> function,
		StreamCodec<? super B, T2> streamCodec2,
		Function<C, T2> function2,
		StreamCodec<? super B, T3> streamCodec3,
		Function<C, T3> function3,
		StreamCodec<? super B, T4> streamCodec4,
		Function<C, T4> function4,
		StreamCodec<? super B, T5> streamCodec5,
		Function<C, T5> function5,
		StreamCodec<? super B, T6> streamCodec6,
		Function<C, T6> function6,
		Function6<T1, T2, T3, T4, T5, T6, C> function62
	) {
		return new StreamCodec<B, C>() {
			@Override
			public C decode(B object) {
				T1 object2 = streamCodec.decode(object);
				T2 object3 = streamCodec2.decode(object);
				T3 object4 = streamCodec3.decode(object);
				T4 object5 = streamCodec4.decode(object);
				T5 object6 = streamCodec5.decode(object);
				T6 object7 = streamCodec6.decode(object);
				return function62.apply(object2, object3, object4, object5, object6, object7);
			}

			@Override
			public void encode(B object, C object2) {
				streamCodec.encode(object, function.apply(object2));
				streamCodec2.encode(object, function2.apply(object2));
				streamCodec3.encode(object, function3.apply(object2));
				streamCodec4.encode(object, function4.apply(object2));
				streamCodec5.encode(object, function5.apply(object2));
				streamCodec6.encode(object, function6.apply(object2));
			}
		};
	}

	static <B, C, T1, T2, T3, T4, T5, T6, T7> StreamCodec<B, C> composite(
		StreamCodec<? super B, T1> streamCodec,
		Function<C, T1> function,
		StreamCodec<? super B, T2> streamCodec2,
		Function<C, T2> function2,
		StreamCodec<? super B, T3> streamCodec3,
		Function<C, T3> function3,
		StreamCodec<? super B, T4> streamCodec4,
		Function<C, T4> function4,
		StreamCodec<? super B, T5> streamCodec5,
		Function<C, T5> function5,
		StreamCodec<? super B, T6> streamCodec6,
		Function<C, T6> function6,
		StreamCodec<? super B, T7> streamCodec7,
		Function<C, T7> function7,
		Function7<T1, T2, T3, T4, T5, T6, T7, C> function72
	) {
		return new StreamCodec<B, C>() {
			@Override
			public C decode(B object) {
				T1 object2 = streamCodec.decode(object);
				T2 object3 = streamCodec2.decode(object);
				T3 object4 = streamCodec3.decode(object);
				T4 object5 = streamCodec4.decode(object);
				T5 object6 = streamCodec5.decode(object);
				T6 object7 = streamCodec6.decode(object);
				T7 object8 = streamCodec7.decode(object);
				return function72.apply(object2, object3, object4, object5, object6, object7, object8);
			}

			@Override
			public void encode(B object, C object2) {
				streamCodec.encode(object, function.apply(object2));
				streamCodec2.encode(object, function2.apply(object2));
				streamCodec3.encode(object, function3.apply(object2));
				streamCodec4.encode(object, function4.apply(object2));
				streamCodec5.encode(object, function5.apply(object2));
				streamCodec6.encode(object, function6.apply(object2));
				streamCodec7.encode(object, function7.apply(object2));
			}
		};
	}

	static <B, C, T1, T2, T3, T4, T5, T6, T7, T8> StreamCodec<B, C> composite(
		StreamCodec<? super B, T1> streamCodec,
		Function<C, T1> function,
		StreamCodec<? super B, T2> streamCodec2,
		Function<C, T2> function2,
		StreamCodec<? super B, T3> streamCodec3,
		Function<C, T3> function3,
		StreamCodec<? super B, T4> streamCodec4,
		Function<C, T4> function4,
		StreamCodec<? super B, T5> streamCodec5,
		Function<C, T5> function5,
		StreamCodec<? super B, T6> streamCodec6,
		Function<C, T6> function6,
		StreamCodec<? super B, T7> streamCodec7,
		Function<C, T7> function7,
		StreamCodec<? super B, T8> streamCodec8,
		Function<C, T8> function8,
		Function8<T1, T2, T3, T4, T5, T6, T7, T8, C> function82
	) {
		return new StreamCodec<B, C>() {
			@Override
			public C decode(B object) {
				T1 object2 = streamCodec.decode(object);
				T2 object3 = streamCodec2.decode(object);
				T3 object4 = streamCodec3.decode(object);
				T4 object5 = streamCodec4.decode(object);
				T5 object6 = streamCodec5.decode(object);
				T6 object7 = streamCodec6.decode(object);
				T7 object8 = streamCodec7.decode(object);
				T8 object9 = streamCodec8.decode(object);
				return function82.apply(object2, object3, object4, object5, object6, object7, object8, object9);
			}

			@Override
			public void encode(B object, C object2) {
				streamCodec.encode(object, function.apply(object2));
				streamCodec2.encode(object, function2.apply(object2));
				streamCodec3.encode(object, function3.apply(object2));
				streamCodec4.encode(object, function4.apply(object2));
				streamCodec5.encode(object, function5.apply(object2));
				streamCodec6.encode(object, function6.apply(object2));
				streamCodec7.encode(object, function7.apply(object2));
				streamCodec8.encode(object, function8.apply(object2));
			}
		};
	}

	static <B, C, T1, T2, T3, T4, T5, T6, T7, T8, T9> StreamCodec<B, C> composite(
		StreamCodec<? super B, T1> streamCodec,
		Function<C, T1> function,
		StreamCodec<? super B, T2> streamCodec2,
		Function<C, T2> function2,
		StreamCodec<? super B, T3> streamCodec3,
		Function<C, T3> function3,
		StreamCodec<? super B, T4> streamCodec4,
		Function<C, T4> function4,
		StreamCodec<? super B, T5> streamCodec5,
		Function<C, T5> function5,
		StreamCodec<? super B, T6> streamCodec6,
		Function<C, T6> function6,
		StreamCodec<? super B, T7> streamCodec7,
		Function<C, T7> function7,
		StreamCodec<? super B, T8> streamCodec8,
		Function<C, T8> function8,
		StreamCodec<? super B, T9> streamCodec9,
		Function<C, T9> function9,
		Function9<T1, T2, T3, T4, T5, T6, T7, T8, T9, C> function92
	) {
		return new StreamCodec<B, C>() {
			@Override
			public C decode(B object) {
				T1 object2 = streamCodec.decode(object);
				T2 object3 = streamCodec2.decode(object);
				T3 object4 = streamCodec3.decode(object);
				T4 object5 = streamCodec4.decode(object);
				T5 object6 = streamCodec5.decode(object);
				T6 object7 = streamCodec6.decode(object);
				T7 object8 = streamCodec7.decode(object);
				T8 object9 = streamCodec8.decode(object);
				T9 object10 = streamCodec9.decode(object);
				return function92.apply(object2, object3, object4, object5, object6, object7, object8, object9, object10);
			}

			@Override
			public void encode(B object, C object2) {
				streamCodec.encode(object, function.apply(object2));
				streamCodec2.encode(object, function2.apply(object2));
				streamCodec3.encode(object, function3.apply(object2));
				streamCodec4.encode(object, function4.apply(object2));
				streamCodec5.encode(object, function5.apply(object2));
				streamCodec6.encode(object, function6.apply(object2));
				streamCodec7.encode(object, function7.apply(object2));
				streamCodec8.encode(object, function8.apply(object2));
				streamCodec9.encode(object, function9.apply(object2));
			}
		};
	}

	static <B, C, T1, T2, T3, T4, T5, T6, T7, T8, T9, T10> StreamCodec<B, C> composite(
		StreamCodec<? super B, T1> streamCodec,
		Function<C, T1> function,
		StreamCodec<? super B, T2> streamCodec2,
		Function<C, T2> function2,
		StreamCodec<? super B, T3> streamCodec3,
		Function<C, T3> function3,
		StreamCodec<? super B, T4> streamCodec4,
		Function<C, T4> function4,
		StreamCodec<? super B, T5> streamCodec5,
		Function<C, T5> function5,
		StreamCodec<? super B, T6> streamCodec6,
		Function<C, T6> function6,
		StreamCodec<? super B, T7> streamCodec7,
		Function<C, T7> function7,
		StreamCodec<? super B, T8> streamCodec8,
		Function<C, T8> function8,
		StreamCodec<? super B, T9> streamCodec9,
		Function<C, T9> function9,
		StreamCodec<? super B, T10> streamCodec10,
		Function<C, T10> function10,
		Function10<T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, C> function102
	) {
		return new StreamCodec<B, C>() {
			@Override
			public C decode(B object) {
				T1 object2 = streamCodec.decode(object);
				T2 object3 = streamCodec2.decode(object);
				T3 object4 = streamCodec3.decode(object);
				T4 object5 = streamCodec4.decode(object);
				T5 object6 = streamCodec5.decode(object);
				T6 object7 = streamCodec6.decode(object);
				T7 object8 = streamCodec7.decode(object);
				T8 object9 = streamCodec8.decode(object);
				T9 object10 = streamCodec9.decode(object);
				T10 object11 = streamCodec10.decode(object);
				return function102.apply(object2, object3, object4, object5, object6, object7, object8, object9, object10, object11);
			}

			@Override
			public void encode(B object, C object2) {
				streamCodec.encode(object, function.apply(object2));
				streamCodec2.encode(object, function2.apply(object2));
				streamCodec3.encode(object, function3.apply(object2));
				streamCodec4.encode(object, function4.apply(object2));
				streamCodec5.encode(object, function5.apply(object2));
				streamCodec6.encode(object, function6.apply(object2));
				streamCodec7.encode(object, function7.apply(object2));
				streamCodec8.encode(object, function8.apply(object2));
				streamCodec9.encode(object, function9.apply(object2));
				streamCodec10.encode(object, function10.apply(object2));
			}
		};
	}

	static <B, C, T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11> StreamCodec<B, C> composite(
		StreamCodec<? super B, T1> streamCodec,
		Function<C, T1> function,
		StreamCodec<? super B, T2> streamCodec2,
		Function<C, T2> function2,
		StreamCodec<? super B, T3> streamCodec3,
		Function<C, T3> function3,
		StreamCodec<? super B, T4> streamCodec4,
		Function<C, T4> function4,
		StreamCodec<? super B, T5> streamCodec5,
		Function<C, T5> function5,
		StreamCodec<? super B, T6> streamCodec6,
		Function<C, T6> function6,
		StreamCodec<? super B, T7> streamCodec7,
		Function<C, T7> function7,
		StreamCodec<? super B, T8> streamCodec8,
		Function<C, T8> function8,
		StreamCodec<? super B, T9> streamCodec9,
		Function<C, T9> function9,
		StreamCodec<? super B, T10> streamCodec10,
		Function<C, T10> function10,
		StreamCodec<? super B, T11> streamCodec11,
		Function<C, T11> function11,
		Function11<T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, C> function112
	) {
		return new StreamCodec<B, C>() {
			@Override
			public C decode(B object) {
				T1 object2 = streamCodec.decode(object);
				T2 object3 = streamCodec2.decode(object);
				T3 object4 = streamCodec3.decode(object);
				T4 object5 = streamCodec4.decode(object);
				T5 object6 = streamCodec5.decode(object);
				T6 object7 = streamCodec6.decode(object);
				T7 object8 = streamCodec7.decode(object);
				T8 object9 = streamCodec8.decode(object);
				T9 object10 = streamCodec9.decode(object);
				T10 object11 = streamCodec10.decode(object);
				T11 object12 = streamCodec11.decode(object);
				return function112.apply(object2, object3, object4, object5, object6, object7, object8, object9, object10, object11, object12);
			}

			@Override
			public void encode(B object, C object2) {
				streamCodec.encode(object, function.apply(object2));
				streamCodec2.encode(object, function2.apply(object2));
				streamCodec3.encode(object, function3.apply(object2));
				streamCodec4.encode(object, function4.apply(object2));
				streamCodec5.encode(object, function5.apply(object2));
				streamCodec6.encode(object, function6.apply(object2));
				streamCodec7.encode(object, function7.apply(object2));
				streamCodec8.encode(object, function8.apply(object2));
				streamCodec9.encode(object, function9.apply(object2));
				streamCodec10.encode(object, function10.apply(object2));
				streamCodec11.encode(object, function11.apply(object2));
			}
		};
	}

	static <B, C, T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12> StreamCodec<B, C> composite(
		StreamCodec<? super B, T1> streamCodec,
		Function<C, T1> function,
		StreamCodec<? super B, T2> streamCodec2,
		Function<C, T2> function2,
		StreamCodec<? super B, T3> streamCodec3,
		Function<C, T3> function3,
		StreamCodec<? super B, T4> streamCodec4,
		Function<C, T4> function4,
		StreamCodec<? super B, T5> streamCodec5,
		Function<C, T5> function5,
		StreamCodec<? super B, T6> streamCodec6,
		Function<C, T6> function6,
		StreamCodec<? super B, T7> streamCodec7,
		Function<C, T7> function7,
		StreamCodec<? super B, T8> streamCodec8,
		Function<C, T8> function8,
		StreamCodec<? super B, T9> streamCodec9,
		Function<C, T9> function9,
		StreamCodec<? super B, T10> streamCodec10,
		Function<C, T10> function10,
		StreamCodec<? super B, T11> streamCodec11,
		Function<C, T11> function11,
		StreamCodec<? super B, T12> streamCodec12,
		Function<C, T12> function12,
		Function12<T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, C> function122
	) {
		return new StreamCodec<B, C>() {
			@Override
			public C decode(B object) {
				T1 object2 = streamCodec.decode(object);
				T2 object3 = streamCodec2.decode(object);
				T3 object4 = streamCodec3.decode(object);
				T4 object5 = streamCodec4.decode(object);
				T5 object6 = streamCodec5.decode(object);
				T6 object7 = streamCodec6.decode(object);
				T7 object8 = streamCodec7.decode(object);
				T8 object9 = streamCodec8.decode(object);
				T9 object10 = streamCodec9.decode(object);
				T10 object11 = streamCodec10.decode(object);
				T11 object12 = streamCodec11.decode(object);
				T12 object13 = streamCodec12.decode(object);
				return function122.apply(object2, object3, object4, object5, object6, object7, object8, object9, object10, object11, object12, object13);
			}

			@Override
			public void encode(B object, C object2) {
				streamCodec.encode(object, function.apply(object2));
				streamCodec2.encode(object, function2.apply(object2));
				streamCodec3.encode(object, function3.apply(object2));
				streamCodec4.encode(object, function4.apply(object2));
				streamCodec5.encode(object, function5.apply(object2));
				streamCodec6.encode(object, function6.apply(object2));
				streamCodec7.encode(object, function7.apply(object2));
				streamCodec8.encode(object, function8.apply(object2));
				streamCodec9.encode(object, function9.apply(object2));
				streamCodec10.encode(object, function10.apply(object2));
				streamCodec11.encode(object, function11.apply(object2));
				streamCodec12.encode(object, function12.apply(object2));
			}
		};
	}

	static <B, T> StreamCodec<B, T> recursive(UnaryOperator<StreamCodec<B, T>> unaryOperator) {
		return new StreamCodec<B, T>() {
			private final Supplier<StreamCodec<B, T>> inner = Suppliers.memoize(() -> unaryOperator.apply(this));

			@Override
			public T decode(B object) {
				return this.inner.get().decode(object);
			}

			@Override
			public void encode(B object, T object2) {
				this.inner.get().encode(object, object2);
			}
		};
	}

	default <S extends B> StreamCodec<S, V> cast() {
		return this;
	}

	@FunctionalInterface
	interface CodecOperation<B, S, T> {
		StreamCodec<B, T> apply(StreamCodec<B, S> streamCodec);
	}
}
