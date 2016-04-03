/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package fj.data;

import fj.F;
import fj.F0;
import fj.Function;
import fj.P;
import fj.P1;
import fj.P2;
import fj.function.Effect0;
import fj.function.Effect1;

/**
 *
 * @author clintonselke
 */
public abstract class Stream2<A> {
  public interface Visitor<A,R> {
    R emit(A a, Stream2<A> next);
    R done();
    R lazy(F0<Stream2<A>> m);
    <B> R bind(Stream2<B> m, F<B,Stream2<A>> f);
  }
  
  public static class AbstractVisitor<A,R> implements Visitor<A,R> {
    private final R default_;
    public AbstractVisitor(R default_) { this.default_ = default_; }
    @Override
    public R emit(A a, Stream2<A> next) {
      return default_;
    }
    @Override
    public R done() {
      return default_;
    }
    @Override
    public R lazy(F0<Stream2<A>> m) {
      return default_;
    }
    @Override
    public <B> R bind(Stream2<B> m, F<B, Stream2<A>> f) {
      return default_;
    }
  }
  
  public abstract <R> R visit(Visitor<A,R> visitor);
  
  public static <A> Stream2<A> emit(A a, Stream2<A> next) {
    return new Stream2<A>() {
      @Override
      public <R> R visit(Visitor<A, R> visitor) {
        return visitor.emit(a, next);
      }
    };
  }
  
  public static <A> Stream2<A> done() {
    return new Stream2<A>() {
      @Override
      public <R> R visit(Visitor<A, R> visitor) {
        return visitor.done();
      }
    };
  }
  
  public static <A> Stream2<A> lazy(F0<Stream2<A>> m) {
    return new Stream2<A>() {
      @Override
      public <R> R visit(Visitor<A, R> visitor) {
        return visitor.lazy(m);
      }
    };
  }
  
  public static <A,B> Stream2<B> _bind(Stream2<A> m, F<A,Stream2<B>> f) {
    return new Stream2<B>() {
      @Override
      public <R> R visit(Visitor<B, R> visitor) {
        return visitor.bind(m, f);
      }
    };
  }
  
  public static <A,B> Stream2<B> bind(Stream2<A> m, F<A,Stream2<B>> f) {
    return m.visit(new Visitor<A,Stream2<B>>() {
      @Override
      public Stream2<B> emit(A a, Stream2<A> next) {
        return Stream2._bind(Stream2.stream(f.f(a), _bind(next, f)), Function.identity());
      }
      @Override
      public Stream2<B> done() {
        return Stream2.done();
      }
      @Override
      public Stream2<B> lazy(F0<Stream2<A>> m) {
        return Stream2.lazy(() -> Stream2.bind(m.f(), f));
      }
      @Override
      public <C> Stream2<B> bind(Stream2<C> m2, F<C, Stream2<A>> f2) {
        return _bind(m2, (C x) -> Stream2.bind(f2.f(x), f));
      }
    });
  }
  
  private final P1<Option<P2<A,Stream2<A>>>> resume_ = new P1<Option<P2<A,Stream2<A>>>>() {
    @Override
    public Option<P2<A, Stream2<A>>> _1() {
      class Util {
        Stream2<A> state;
        boolean done;
        Option<P2<A,Stream2<A>>> result;
      }
      final Util util = new Util();
      util.state = Stream2.this;
      util.done = false;
      final Visitor<A,Effect0> resumeVisitor = new Visitor<A,Effect0>() {
        @Override
        public Effect0 emit(A a, Stream2<A> next) {
          return () -> {
            util.done = true;
            util.result = Option.some(P.p(a, next));
          };
        }
        @Override
        public Effect0 done() {
          return () -> {
            util.done = true;
            util.result = Option.none();
          };
        }
        @Override
        public Effect0 lazy(F0<Stream2<A>> m) {
          return () -> {
            util.state = m.f();
          };
        }
        @Override
        public <B> Effect0 bind(Stream2<B> m, F<B, Stream2<A>> f) {
          return m.visit(new Visitor<B,Effect0>() {
            @Override
            public Effect0 emit(B a, Stream2<B> next) {
              return () -> {
                util.state = f.f(a).append(Stream2.bind(next, f));
              };
            }
            @Override
            public Effect0 done() {
              return () -> {
                util.done = true;
                util.result = Option.none();
              };
            }
            @Override
            public Effect0 lazy(F0<Stream2<B>> m2) {
              return () -> {
                util.state = Stream2.bind(m2.f(), f);
              };
            }
            @Override
            public <C> Effect0 bind(Stream2<C> m2, F<C, Stream2<B>> f2) {
              return () -> {
                util.state = Stream2.bind(m2, (C x) -> Stream2.bind(f2.f(x), f));
              };
            }
          });
        }
      };
      while (!util.done) {
        util.state.visit(resumeVisitor).f();
      }
      return util.result;
    }
  }.softMemo();
  
  public Option<P2<A,Stream2<A>>> resume() {
    return resume_._1();
  }
  
  public <B> B uncons(B nil, F<P2<A,Stream2<A>>,B> cons) {
    return resume().option(nil, cons);
  }
  
  public <B> B uncons(F0<B> nil, F<P2<A,Stream2<A>>,B> cons) {
    return resume().option(nil, cons);
  }
  
  public Option<A> head() {
    return resume().map(P2.__1());
  }
  
  public Option<Stream2<A>> tail() {
    return resume().map(P2.__2());
  }
  
  public static <A> Stream2<A> nil() {
    return done();
  }
  
  public static <A> Stream2<A> stream(A... as) {
    return arrayStream(as);
  }
  
  public static <A> Stream2<A> arrayStream(A[] as) {
    Stream2<A> r = Stream2.done();
    for (int i = as.length-1; i >= 0; --i) {
      r = Stream2.emit(as[i], r);
    }
    return r;
  }
  
  public Stream2<A> filter(F<A,Boolean> f) {
    return this.bind((A x) -> f.f(x) ? Stream2.singleton(x) : Stream2.nil());
  }
  
  public <B> Stream2<B> map(F<A,B> f) {
    return bind(this, (A x) -> singleton(f.f(x)));
  }
  
  public <B> Stream2<B> apply(Stream2<F<A,B>> mf) {
    return bind(mf, (F<A,B> f) -> this.map(f));
  }
  
  public static <A> Stream2<A> singleton(A a) {
    return emit(a, done());
  }
  
  public <B> Stream2<B> bind(F<A,Stream2<B>> f) {
    return bind(this, f);
  }
  
  public Stream2<A> append(Stream2<A> as) {
    return Stream2.stream(this, as).bind(Function.identity());
  }
  
  public final void foreachDoEffect(final Effect1<A> f) {
    Option<Stream2<A>> stateOp = Option.some(this);
    while (stateOp.isSome()) {
      for (Stream2<A> state : stateOp) {
        Option<P2<A,Stream2<A>>> x = state.resume();
        for (P2<A,Stream2<A>> y : x) {
          f.f(y._1());
        }
        stateOp = x.map(P2.__2());
      }
    }
  }
}
