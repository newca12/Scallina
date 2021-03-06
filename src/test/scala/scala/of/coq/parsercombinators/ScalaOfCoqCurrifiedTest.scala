package scala.of.coq.parsercombinators

import org.scalatest.FunSuite
import org.scalatest.Matchers.convertToAnyShouldWrapper

import scala.of.coq.parsercombinators.CustomMatchers.generateScalaCode
import scala.of.coq.parsercombinators.TestUtils.coqParserShouldFailToGenerateScalaCodeFor
import scala.of.coq.parsercombinators.compiler.Currify
import scala.of.coq.parsercombinators.parser.CoqParser

class ScalaOfCoqCurrifiedTest extends FunSuite {

  implicit val curryingStrategy: Currify.type = Currify

  test("""Testing that a different signature between record definition and instanciation is not supported
        Require Import Coq.Lists.List.

        Record Queue := {
          t : Type;
          empty : t;
          push (x: nat) (l: t): t;
          pop (l: t): option (nat * t)
        }.

        Definition ListQueue : Queue := {|
          t := list nat;
          empty := nil;
          push := fun x l => x :: l;
          pop l :=
            match rev l with
              | nil => None
              | hd :: tl => Some (hd, rev tl)
            end
        |}.
       """) {
    coqParserShouldFailToGenerateScalaCodeFor("""
        Require Import Coq.Lists.List.

        Record Queue := {
          t : Type;
          empty : t;
          push (x: nat) (l: t): t;
          pop (l: t): option (nat * t)
        }.

        Definition ListQueue : Queue := {|
          t := list nat;
          empty := nil;
          push := fun x l => x :: l;
          pop l :=
            match rev l with
              | nil => None
              | hd :: tl => Some (hd, rev tl)
            end
        |}.
        """)
  }

  test("""Testing that a different signature between record definition and instanciation is not supported
          Require Import Coq.Lists.List.

          Record TestRecord := {
            test (x y: nat) (l: list nat) : nat
          }.

          Definition RecordInstance : TestRecord := {|
            test a := fun _ _ => a
          |}.
       """) {
    coqParserShouldFailToGenerateScalaCodeFor("""
          Require Import Coq.Lists.List.

          Record TestRecord := {
            test (x y: nat) (l: list nat) : nat
          }.

          Definition RecordInstance : TestRecord := {|
            test a := fun _ _ => a
          |}.
        """)
  }

  test("""Testing that Gallina path-depdendent types are restricted to the ones starting with an identifier
        Definition f (Q: Queue) : Queue := Q.

        Definition createQueue (Q: Queue) (n: nat) : (f Q).(T) := insertElems Q Q.(empty) n.
       """) {
    coqParserShouldFailToGenerateScalaCodeFor(
      """
        Definition f (Q: Queue) : Queue := Q.

        Definition createQueue (Q: Queue) (n: nat) : (f Q).(T) := insertElems Q Q.(empty) n.
        """
    )
  }

  test("""Testing Scala conversion of
      Definition t1 {A B C} (f: A -> B) (g: B -> C) : A -> C :=
        fun (x : A) => g (f x).
    """) {
    CoqParser("""
      Definition t1 {A B C} (f: A -> B) (g: B -> C) : A -> C :=
        fun (x : A) => g (f x).
      """) should generateScalaCode("""
      "def t1[A, B, C](f: A => B)(g: B => C): A => C =
      "  (x: A) => g(f(x))
      """)
  }

  test("""Testing Scala conversion of
        Definition curryAdd : Z -> Z -> Z :=
          fun (x y : Z) => x.
    """) {
    CoqParser("""
        Definition curryAdd : Z -> Z -> Z :=
          fun (x y : Z) => x + y.
      """) should generateScalaCode("""
      "def curryAdd: BigInt => BigInt => BigInt =
      "  (x: BigInt) => (y: BigInt) => x + y
      """)
  }

  test("""Testing Scala conversion of
      Definition plus (a b : Z) : Z := a + b.
      Definition higherOrder (f: Z -> Z -> Z) (a b : Z) : Z := f a b.
      Definition plusAgain : Z -> Z -> Z := higherOrder plus.
    """) {
    CoqParser("""
      Definition plus (a b : Z) : Z := a + b.
      Definition higherOrder (f: Z -> Z -> Z) (a b : Z) : Z := f a b.
      Definition plusAgain : Z -> Z -> Z := higherOrder plus.
      """) should generateScalaCode(
      """
      "def plus(a: BigInt)(b: BigInt): BigInt = a + b
      "def higherOrder(f: BigInt => BigInt => BigInt)(a: BigInt)(b: BigInt): BigInt = f(a)(b)
      "def plusAgain: BigInt => BigInt => BigInt = higherOrder(plus)
      """
    )
  }

  test("""Testing Scala conversion of
        Definition plus (a b : nat) : nat := a + b.
        Definition higherOrder (f: nat -> nat -> nat) (a b : nat) : nat := f a b.
        Definition plusAgain : nat -> nat -> nat := higherOrder plus.
    """) {
    CoqParser("""
        Definition plus (a b : nat) : nat := a + b.
        Definition higherOrder (f: nat -> nat -> nat) (a b : nat) : nat := f a b.
        Definition plusAgain : nat -> nat -> nat := higherOrder plus.
      """) should generateScalaCode("""
      "def plus(a: Nat)(b: Nat): Nat = a + b
      "def higherOrder(f: Nat => Nat => Nat)(a: Nat)(b: Nat): Nat = f(a)(b)
      "def plusAgain: Nat => Nat => Nat = higherOrder(plus)
      """)
  }

  test("""Testing Scala conversion of
    Definition testSimpleLetWithBinders (x: nat) : nat :=
      let f (a b : nat) := a * b in f 7 3.
       """) {
    CoqParser("""
      Definition testSimpleLetWithBinders (x: nat) : nat :=
        let f (a b : nat) := a * b in f 7 3.
      """) should generateScalaCode("""
      "def testSimpleLetWithBinders(x: Nat): Nat = {
      "  val f = (a: Nat) => (b: Nat) => a * b
      "  f(7)(3)
      "}
      """)
  }

  test("""Testing Scala conversion of
        Require Import ZArith.
        Open Scope Z_scope.

        Definition squareDistance (a b: Z * Z) : Z :=
        let (x1, y1) := a in
        let ' pair x2 y2 := b in
        let square (u: Z) := u * u in
        let x := x2 - x1 in
        let y := y2 - y1 in
        (square x) + (square y).
       """) {
    CoqParser("""
        Require Import ZArith.
        Open Scope Z_scope.

        Definition squareDistance (a b: Z * Z) : Z :=
        let (x1, y1) := a in
        let ' pair x2 y2 := b in
        let square (u: Z) := u * u in
        let x := x2 - x1 in
        let y := y2 - y1 in
        (square x) + (square y).
      """) should generateScalaCode("""
      "def squareDistance(a: (BigInt, BigInt))(b: (BigInt, BigInt)): BigInt = {
      "  val (x1, y1) = a
      "  val Tuple2(x2, y2) = b
      "  val square = (u: BigInt) => u * u
      "  val x = x2 - x1
      "  val y = y2 - y1
      "  square(x) + square(y)
      "}
      """)
  }

  test("""Testing Scala conversion of
      Function merge {A} (less: A -> A -> bool) (z: (list A) * (list A))
      { measure (fun z => length (fst z) + length (snd z)) z } : list A :=
       let (l1, l2) := z in
       match l1 with
       | nil => l2
       | x1::l1_ =>
         match l2 with
         | nil => l1
         | x2::l2_ =>
           if less x1 x2 then x1 :: merge less (l1_, l2)
           else x2 :: merge less (l1, l2_)
         end
       end.

      Proof.
       - intros.
         auto.
       - intros.
         simpl.
         apply Lt.lt_n_S.
         apply Plus.plus_lt_compat_l.
         auto.
      Qed.
       """) {
    CoqParser("""
      Function merge {A} (less: A -> A -> bool) (z: (list A) * (list A))
      { measure (fun z => length (fst z) + length (snd z)) z } : list A :=
       let (l1, l2) := z in
       match l1 with
       | nil => l2
       | x1::l1_ =>
         match l2 with
         | nil => l1
         | x2::l2_ =>
           if less x1 x2 then x1 :: merge less (l1_, l2)
           else x2 :: merge less (l1, l2_)
         end
       end.

      Proof.
       - intros.
         auto.
       - intros.
         simpl.
         apply Lt.lt_n_S.
         apply Plus.plus_lt_compat_l.
         auto.
      Qed.
      """) should generateScalaCode("""
      "def merge[A](less: A => A => Boolean)(z: (List[A], List[A])): List[A] = {
      "  val (l1, l2) = z
      "  l1 match {
      "    case Nil => l2
      "    case x1 :: l1_ => l2 match {
      "      case Nil => l1
      "      case x2 :: l2_ => if (less(x1)(x2)) x1 :: merge(less)((l1_, l2))
      "      else x2 :: merge(less)((l1, l2_))
      "    }
      "  }
      "}
      """)
  }

  test("""Testing Scala conversion of
      Function lenTailrec {A} (xs : list A) (n : nat) { measure (fun xs => length(xs)) xs } : nat :=
      match xs with
      | nil => n
      | _ :: ys => lenTailrec ys (1 + n)
      end.
      Proof.
        intros.
        simpl.
        omega.
      Qed.
       """) {
    CoqParser("""
      Function lenTailrec {A} (xs : list A) (n : nat) { measure (fun xs => length(xs)) xs } : nat :=
      match xs with
      | nil => n
      | _ :: ys => lenTailrec ys (1 + n)
      end.
      Proof.
        intros.
        simpl.
        omega.
      Qed.
      """) should generateScalaCode("""
      "def lenTailrec[A](xs: List[A])(n: Nat): Nat =
      "  xs match {
      "    case Nil     => n
      "    case _ :: ys => lenTailrec(ys)(1 + n)
      "  }
      """)
  }

  test("""Testing Scala conversion of
          Inductive Tree :=
            Leaf
          | Node (value: nat) (l r: Tree).
       """) {
    CoqParser("""
          Inductive Tree :=
            Leaf
          | Node (value: nat) (l r: Tree).
      """) should generateScalaCode("""
        "sealed abstract class Tree
        "case object Leaf extends Tree
        "case class Node(value: Nat, l: Tree, r: Tree) extends Tree
        "object Node {
        "  def apply =
        "    (value: Nat) => (l: Tree) => (r: Tree) => new Node(value, l, r)
        "}
        """)
  }

  test("""Testing Scala conversion of
            Inductive Tree A :=
              Leaf
            | Node (value: A) (l r: Tree A).

            Arguments Leaf {A}.
            Arguments Node {A} _ _ _.
       """) {
    CoqParser("""
            Inductive Tree A :=
              Leaf
            | Node (value: A) (l r: Tree A).

            Arguments Leaf {A}.
            Arguments Node {A} _ _ _.
      """) should generateScalaCode("""
        "sealed abstract class Tree[+A]
        "case object Leaf extends Tree[Nothing]
        "case class Node[A](value: A, l: Tree[A], r: Tree[A]) extends Tree[A]
        "object Node {
        "  def apply[A] =
        "    (value: A) => (l: Tree[A]) => (r: Tree[A]) => new Node(value, l, r)
        "}
        """)
  }

  test("""Testing Scala conversion of
          Inductive Tree {A B : Type} : Type :=
          | Leaf {C : Type} (firstValue: A) (secondValue: C)
          | Node {D} (firstValue: B) (secondValue: D) (left: @Tree A B) (right: @Tree A B).
       """) {
    CoqParser("""
          Inductive Tree {A B : Type} : Type :=
          | Leaf {C : Type} (v1: A) (v2: C)
          | Node {D} (v1: B) (v2: D) (l: @Tree A B) (r: @Tree A B).
      """) should generateScalaCode(
      """
        "sealed abstract class Tree[+A, +B]
        "case class Leaf[A, B, C](v1: A, v2: C) extends Tree[A, B]
        "object Leaf {
        "  def apply[A, B, C] =
        "    (v1: A) => (v2: C) => new Leaf(v1, v2)
        "}
        "case class Node[A, B, D](v1: B, v2: D, l: Tree[A, B], r: Tree[A, B]) extends Tree[A, B]
        "object Node {
        "  def apply[A, B, D] =
        "    (v1: B) => (v2: D) => (l: Tree[A, B]) => (r: Tree[A, B]) => new Node(v1, v2, l, r)
        "}
        """
    )
  }

  test("""Testing Scala conversion of
          Require Import Omega.

          Inductive Tree (A: Type) :=
            Leaf(value: A)
          | Node(l r : Tree A).

          Arguments Leaf {A} _.
          Arguments Node {A} _ _.

          Fixpoint size {A} (t: Tree A) : nat :=
          match t with
            Leaf _ => 1
          | Node l r => 1 + (size l) + (size r)
          end.

          Lemma size_left: forall A (l r: Tree A), size (Node l r) > size l.
          Proof.
            intros; induction l; simpl; omega.
          Qed.

          Fixpoint map {A B} (t: Tree A) (f: A -> B) : Tree B :=
          match t with
            Leaf a => Leaf (f a)
          | Node l r => Node (map l f) (map r f)
          end.
       """) {
    CoqParser("""
          Require Import Omega.

          Inductive Tree (A: Type) :=
            Leaf(value: A)
          | Node(l r : Tree A).

          Arguments Leaf {A} _.
          Arguments Node {A} _ _.

          Fixpoint size {A} (t: Tree A) : nat :=
          match t with
            Leaf _ => 1
          | Node l r => 1 + (size l) + (size r)
          end.

          Lemma size_left: forall A (l r: Tree A), size (Node l r) > size l.
          Proof.
            intros; induction l; simpl; omega.
          Qed.

          Fixpoint map {A B} (t: Tree A) (f: A -> B) : Tree B :=
          match t with
            Leaf a => Leaf (f a)
          | Node l r => Node (map l f) (map r f)
          end.
      """) should generateScalaCode("""
        "sealed abstract class Tree[+A]
        "case class Leaf[A](value: A) extends Tree[A]
        "case class Node[A](l: Tree[A], r: Tree[A]) extends Tree[A]
        "object Node {
        "  def apply[A] =
        "    (l: Tree[A]) => (r: Tree[A]) => new Node(l, r)
        "}
        "def size[A](t: Tree[A]): Nat =
        "  t match {
        "    case Leaf(_)    => 1
        "    case Node(l, r) => 1 + (size(l) + size(r))
        "  }
        "def map[A, B](t: Tree[A])(f: A => B): Tree[B] =
        "  t match {
        "    case Leaf(a)    => Leaf(f(a))
        "    case Node(l, r) => Node(map(l)(f))(map(r)(f))
        "  }
        """)
  }

  test("""Testing Scala conversion of
        Inductive Tree A := Leaf | Node (v: A) (l r: Tree A).
        Arguments Leaf {A}.
        Arguments Node {A} _ _ _.
        Fixpoint map {A B} (t: Tree A) (f: A -> B) : Tree B :=
        match t with
          Leaf => Leaf
        | Node v l r => Node (f v) (map l f) (map r f)
        end.
       """) {
    CoqParser("""
        Inductive Tree A := Leaf | Node (v: A) (l r: Tree A).
        Arguments Leaf {A}.
        Arguments Node {A} _ _ _.
        Fixpoint map {A B} (t: Tree A) (f: A -> B) : Tree B :=
        match t with
          Leaf => Leaf
        | Node v l r => Node (f v) (map l f) (map r f)
        end.
      """) should generateScalaCode("""
      "sealed abstract class Tree[+A]
      "case object Leaf extends Tree[Nothing]
      "case class Node[A](v: A, l: Tree[A], r: Tree[A]) extends Tree[A]
      "object Node {
      "  def apply[A] =
      "    (v: A) => (l: Tree[A]) => (r: Tree[A]) => new Node(v, l, r)
      "}
      "def map[A, B](t: Tree[A])(f: A => B): Tree[B] =
      "  t match {
      "    case Leaf          => Leaf
      "    case Node(v, l, r) => Node(f(v))(map(l)(f))(map(r)(f))
      "  }
      """)
  }

  test("""Testing Scala conversion of the example by P. Letouzey in ``Extraction in Coq: An Overview''
        Record aMonoid : Type := {
          dom : Type;
          zero : dom;
          op : dom -> dom -> dom
        }.
       """) {
    CoqParser("""
        Record aMonoid : Type := {
          dom : Type;
          zero : dom;
          op : dom -> dom -> dom
        }.
      """) should generateScalaCode("""
      "trait aMonoid {
      "  type dom
      "  def zero: dom
      "  def op: dom => dom => dom
      "}
      """)
  }

  test("""Testing Scala conversion of the example by P. Letouzey in ``Extraction in Coq: An Overview''
        Record aMonoid : Type := {
          dom : Type;
          zero : dom;
          op : dom -> dom -> dom
        }.

        Definition natMonoid : aMonoid := {|
          dom := nat;
          zero := 0;
          op := fun (a: nat) (b: nat) => a + b
        |}.
       """) {
    CoqParser("""
      Record aMonoid : Type := {
        dom : Type;
        zero : dom;
        op : dom -> dom -> dom
      }.

      Definition natMonoid : aMonoid := {|
        dom := nat;
        zero := 0;
        op := fun (a: nat) (b: nat) => a + b
      |}.
      """) should generateScalaCode("""
      "trait aMonoid {
      "  type dom
      "  def zero: dom
      "  def op: dom => dom => dom
      "}
      "object natMonoid extends aMonoid {
      "  type dom = Nat
      "  def zero: dom = 0
      "  def op: dom => dom => dom = (a: Nat) => (b: Nat) => a + b
      "}
      """)
  }

  test("""Testing Scala conversion of the example by P. Letouzey in ``Extraction in Coq: An Overview''
      Record aMonoid : Type := newMonoid {
        dom : Type;
        zero : dom;
        op : dom -> dom -> dom
      }.

      Definition natMonoid := newMonoid nat 0 (fun (a: nat) (b: nat) => a + b).
       """) {
    CoqParser("""
      Record aMonoid : Type := newMonoid {
        dom : Type;
        zero : dom;
        op : dom -> dom -> dom
      }.

      Definition natMonoid := newMonoid nat 0 (fun (a: nat) (b: nat) => a + b).
      """) should generateScalaCode("""
      "trait aMonoid {
      "  type dom
      "  def zero: dom
      "  def op: dom => dom => dom
      "}
      "def newMonoid[dom](zero: dom)(op: dom => dom => dom): aMonoid = {
      "  type aMonoid_dom = dom
      "  def aMonoid_zero = zero
      "  def aMonoid_op = op
      "  new aMonoid {
      "    type dom = aMonoid_dom
      "    def zero: dom = aMonoid_zero
      "    def op: dom => dom => dom = aMonoid_op
      "  }
      "}
      "def natMonoid = newMonoid[Nat](0)((a: Nat) => (b: Nat) => a + b)
      """)
  }

  test("""Testing Scala conversion of the example by P. Letouzey in ``Extraction in Coq: An Overview''
      Require Import ZArith.

      Open Scope Z_scope.

      Record aMonoid : Type := {
        dom : Type;
        zero : dom;
        op : dom -> dom -> dom
      }.

      Definition intMonoid : aMonoid := {|
        dom := Z;
        zero := 0;
        op := fun (a: Z) (b: Z) => a + b
      |}.
       """) {
    CoqParser("""
      Require Import ZArith.

      Open Scope Z_scope.

      Record aMonoid : Type := {
        dom : Type;
        zero : dom;
        op : dom -> dom -> dom
      }.

      Definition intMonoid : aMonoid := {|
        dom := Z;
        zero := 0;
        op := fun (a: Z) (b: Z) => a + b
      |}.
      """) should generateScalaCode("""
      "trait aMonoid {
      "  type dom
      "  def zero: dom
      "  def op: dom => dom => dom
      "}
      "object intMonoid extends aMonoid {
      "  type dom = BigInt
      "  def zero: dom = 0
      "  def op: dom => dom => dom = (a: BigInt) => (b: BigInt) => a + b
      "}
      """)
  }

  test("""Testing Scala conversion of the example by P. Letouzey in ``Extraction in Coq: An Overview''
      Record aMonoid : Type := {
        dom : Type;
        zero : dom;
        op (a: dom) (b: dom): dom
      }.
      Definition genMonoid {A} (z: A) (f: A -> A -> A) : aMonoid := {|
        dom := A;
        zero := z;
        op a b := f a b
      |}.
       """) {
    CoqParser("""
      Record aMonoid : Type := {
        dom : Type;
        zero : dom;
        op (a: dom) (b: dom): dom
      }.
      Definition genMonoid {A} (z: A) (f: A -> A -> A) : aMonoid := {|
        dom := A;
        zero := z;
        op a b := f a b
      |}.
      """) should generateScalaCode("""
      "trait aMonoid {
      "  type dom
      "  def zero: dom
      "  def op: dom => dom => dom
      "}
      "def genMonoid[A](z: A)(f: A => A => A): aMonoid = new aMonoid {
      "  type dom = A
      "  def zero: dom = z
      "  def op: dom => dom => dom = (a: dom) => (b: dom) => f(a)(b)
      "}
      """)
  }

  test("""Testing Scala conversion of the example by P. Letouzey in ``Extraction in Coq: An Overview''
      Record aMonoid : Type := {
        dom : Type;
        zero : dom;
        op: dom -> dom -> dom
      }.
      Definition genMonoid {A} (z: A) (f: A -> A -> A) : aMonoid := {|
        dom := A;
        zero := z;
        op := fun a b => f a b
      |}.
       """) {
    CoqParser("""
      Record aMonoid : Type := {
        dom : Type;
        zero : dom;
        op: dom -> dom -> dom
      }.
      Definition genMonoid {A} (z: A) (f: A -> A -> A) : aMonoid := {|
        dom := A;
        zero := z;
        op := fun a b => f a b
      |}.
      """) should generateScalaCode("""
      "trait aMonoid {
      "  type dom
      "  def zero: dom
      "  def op: dom => dom => dom
      "}
      "def genMonoid[A](z: A)(f: A => A => A): aMonoid = new aMonoid {
      "  type dom = A
      "  def zero: dom = z
      "  def op: dom => dom => dom = a => b => f(a)(b)
      "}
      """)
  }

  test("""Testing Scala conversion of the example by P. Letouzey in ``Extraction in Coq: An Overview''
      Record aMonoid : Type := {
        dom : Type;
        zero : dom;
        op: dom -> dom -> dom
      }.
      Definition execute_op(m: aMonoid) (a b: m.(dom)) : m.(dom) := m.(op) a b.
       """) {
    CoqParser("""
      Record aMonoid : Type := {
        dom : Type;
        zero : dom;
        op: dom -> dom -> dom
      }.
      Definition execute_op(m: aMonoid) (a b: m.(dom)) : m.(dom) := m.(op) a b.
      """) should generateScalaCode("""
      "trait aMonoid {
      "  type dom
      "  def zero: dom
      "  def op: dom => dom => dom
      "}
      "def execute_op(m: aMonoid)(a: m.dom)(b: m.dom): m.dom = m.op(a)(b)
      """)
  }

  test("""Testing Scala conversion of
        Record Queue := {
          T : Type;
          empty : T;
          push : nat -> T -> T;
          pop : T -> option (nat * T)
        }.
       """) {
    CoqParser("""
        Record Queue := {
          T : Type;
          empty : T;
          push : nat -> T -> T;
          pop : T -> option (nat * T)
        }.
      """) should generateScalaCode("""
      "trait Queue {
      "  type T
      "  def empty: T
      "  def push: Nat => T => T
      "  def pop: T => Option[(Nat, T)]
      "}
      """)
  }

  test("""Testing Scala conversion of
          Record Queue := newQueue {
            T : Type;
            empty : T;
            push (x : nat) (q : T) : T;
            pop (q: T) : option (nat * T)
          }.
       """) {
    CoqParser("""
      Record Queue := newQueue {
        T : Type;
        empty : T;
        push (x : nat) (q : T) : T;
        pop (q: T) : option (nat * T)
      }.
      """) should generateScalaCode(
      """
      "trait Queue {
      "  type T
      "  def empty: T
      "  def push: Nat => T => T
      "  def pop: T => Option[(Nat, T)]
      "}
      "def newQueue[T](empty: T)(push: Nat => T => T)(pop: T => Option[(Nat, T)]): Queue = {
      "  type Queue_T = T
      "  def Queue_empty = empty
      "  def Queue_push = push
      "  def Queue_pop = pop
      "  new Queue {
      "    type T = Queue_T
      "    def empty: T = Queue_empty
      "    def push: Nat => T => T = Queue_push
      "    def pop: T => Option[(Nat, T)] = Queue_pop
      "  }
      "}
      """
    )
  }

  test("""Testing Scala conversion of
          Fixpoint insertElems (Q: Queue) (q: Q.(T)) (n: nat) : Q.(T) :=
          match n with
            0 => q
          | S m => Q.(push) n (insertElems Q q m)
          end.

          Definition createQueue (Q: Queue) (n: nat) : Q.(T) := insertElems Q Q.(empty) n.

          Definition createListQueue(n: nat) := createQueue ListQueue n.

          Definition createDListQueue(n: nat) := createQueue DListQueue n.
       """) {
    CoqParser("""
          Fixpoint insertElems (Q: Queue) (q: Q.(T)) (n: nat) : Q.(T) :=
          match n with
            0 => q
          | S m => Q.(push) n (insertElems Q q m)
          end.

          Definition createQueue (Q: Queue) (n: nat) : Q.(T) := insertElems Q Q.(empty) n.

          Definition createListQueue(n: nat) := createQueue ListQueue n.

          Definition createDListQueue(n: nat) := createQueue DListQueue n.
      """) should generateScalaCode("""
      "def insertElems(Q: Queue)(q: Q.T)(n: Nat): Q.T =
      "  n match {
      "    case Zero => q
      "    case S(m) => Q.push(n)(insertElems(Q)(q)(m))
      "  }
      "def createQueue(Q: Queue)(n: Nat): Q.T = insertElems(Q)(Q.empty)(n)
      "def createListQueue(n: Nat) = createQueue(ListQueue)(n)
      "def createDListQueue(n: Nat) = createQueue(DListQueue)(n)
      """)
  }

  test("""Testing Scala conversion of
        Record TestRecord :=
        Build_TestRecord {
          testAbstractField : nat;
          testConcreteField : nat := testAbstractField + 3;
          testFunction (x : nat) : nat := x + 7;
          testAnonFun : nat -> nat := fun (x : nat) => x + 3
        }.
       """) {
    CoqParser("""
      Record TestRecord :=
      Build_TestRecord {
        testAbstractField : nat;
        testConcreteField : nat := testAbstractField + 3;
        testFunction (x : nat) : nat := x + 7;
        testAnonFun : nat -> nat := fun (x : nat) => x + 3
      }.
      """) should generateScalaCode("""
      "trait TestRecord {
      "  def testAbstractField: Nat
      "  def testConcreteField: Nat = testAbstractField + 3
      "  def testFunction: Nat => Nat = (x: Nat) => x + 7
      "  def testAnonFun: Nat => Nat = (x: Nat) => x + 3
      "}
      "def Build_TestRecord(testAbstractField: Nat): TestRecord = {
      "  def TestRecord_testAbstractField = testAbstractField
      "  new TestRecord {
      "    def testAbstractField: Nat = TestRecord_testAbstractField
      "  }
      "}
      """)
  }

  test("""Testing Scala conversion of
        Record TestRecord {A} :=
        Build_TestRecord {
          testAbstractField : A;
          testConcreteField : A := testAbstractField;
          testFunction (x : A) : A := x;
          testAnonFun : A -> A := fun (x : A) => x
        }.
       """) {
    CoqParser("""
      Record TestRecord {A} :=
      Build_TestRecord {
        testAbstractField : A;
        testConcreteField : A := testAbstractField;
        testFunction (x : A) : A := x;
        testAnonFun : A -> A := fun (x : A) => x
      }.
      """) should generateScalaCode("""
      "trait TestRecord[A] {
      "  def testAbstractField: A
      "  def testConcreteField: A = testAbstractField
      "  def testFunction: A => A = (x: A) => x
      "  def testAnonFun: A => A = (x: A) => x
      "}
      "def Build_TestRecord[A](testAbstractField: A): TestRecord[A] = {
      "  def TestRecord_testAbstractField = testAbstractField
      "  new TestRecord[A] {
      "    def testAbstractField: A = TestRecord_testAbstractField
      "  }
      "}
      """)
  }

  test("""Testing Scala conversion of
          Record TestRecord {A B} :=
          newTestRecord {
            f1 : A;
            f2 : B -> A
          }.
       """) {
    CoqParser("""
      Record TestRecord {A B} :=
      newTestRecord {
        f1 : A;
        f2 : B -> A
      }.
      """) should generateScalaCode("""
      "trait TestRecord[A, B] {
      "  def f1: A
      "  def f2: B => A
      "}
      "def newTestRecord[A, B](f1: A)(f2: B => A): TestRecord[A, B] = {
      "  def TestRecord_f1 = f1
      "  def TestRecord_f2 = f2
      "  new TestRecord[A, B] {
      "    def f1: A = TestRecord_f1
      "    def f2: B => A = TestRecord_f2
      "  }
      "}
      """)
  }

  test("""Testing Scala conversion of
      Definition testRecordFunction {A B} (R: @ TestRecord A B) := R.(f1).
      Definition test1 := testRecordInstance.(f1).
      Definition test2 := testRecordInstance.(f2) true.
      Definition test3 := testRecordFunction testRecordInstance.
       """) {
    CoqParser("""
      Definition testRecordFunction {A B} (R: @ TestRecord A B) := R.(f1).
      Definition test1 := testRecordInstance.(f1).
      Definition test2 := testRecordInstance.(f2) true.
      Definition test3 := testRecordFunction testRecordInstance.
      """) should generateScalaCode("""
      "def testRecordFunction[A, B](R: TestRecord[A, B]) = R.f1
      "def test1 = testRecordInstance.f1
      "def test2 = testRecordInstance.f2(true)
      "def test3 = testRecordFunction(testRecordInstance)
      """)
  }

  // TODO(Joseph Bakouny): Consider implementing the support of concrete type field replacements in the constructor's arguments.
  ignore("""Testing Scala conversion of
          Record ComplicatedRecord :=
          newComplicatedRecord {
            B : Type := nat;
            T (A : Type) : Type := list A;
            f (x: T B) : T B
          }.
       """) {
    CoqParser("""
      Record ComplicatedRecord :=
      newComplicatedRecord {
        B : Type := nat;
        T (A : Type) : Type := list A;
        f (x: T B) : T B
      }.
      """) should generateScalaCode("""
      "trait ComplicatedRecord {
      "  type B = Nat
      "  type T[A] = List[A]
      "  def f: T[B] => T[B]
      "}
      "def newComplicatedRecord(f: List[Nat] => List[Nat]): ComplicatedRecord = {
      "  def ComplicatedRecord_f = f
      "  new ComplicatedRecord {
      "    def f: T[B] => T[B] = ComplicatedRecord_f
      "  }
      "}
      """)
  }

  test("""Testing Scala conversion of
          Definition prepend (a : nat) (l: list nat) := a :: l.
          Definition test := prepend 3 (record.(f) (7 :: nil)).
       """) {
    CoqParser("""
        Definition prepend (a : nat) (l: list nat) := a :: l.
        Definition test := prepend 3 (record.(f) (7 :: nil)).
      """) should generateScalaCode("""
      "def prepend(a: Nat)(l: List[Nat]) = a :: l
      "def test = prepend(3)(record.f(7 :: Nil))
      """)
  }

  test("""Testing Scala conversion of
          Require Import Coq.Lists.List.

          Record Queue := {
            t : Type;
            empty : t;
            push (x: nat) (l: t): t;
            pop (l: t): option (nat * t)
          }.

          Definition ListQueue : Queue := {|
            t := list nat;
            empty := nil;
            push x l := x :: l;
            pop l :=
              match rev l with
                | nil => None
                | hd :: tl => Some (hd, rev tl)
              end
          |}.

          Definition DListQueue : Queue := {|
            t := (list nat) * (list nat);
            empty := (nil, nil);
            push x l :=
              let (back, front) := l in
              (x :: back,front);
            pop l :=
              let (back, front) := l in
              match front with
                | nil =>
                   match rev back with
                      | nil => None
                      | hd :: tl => Some (hd, (nil, tl))
                   end
                | hd :: tl => Some (hd, (back, tl))
              end
          |}.
       """) {
    CoqParser("""
          Require Import Coq.Lists.List.

          Record Queue := {
            t : Type;
            empty : t;
            push (x: nat) (l: t): t;
            pop (l: t): option (nat * t)
          }.

          Definition ListQueue : Queue := {|
            t := list nat;
            empty := nil;
            push x l := x :: l;
            pop l :=
              match rev l with
                | nil => None
                | hd :: tl => Some (hd, rev tl)
              end
          |}.

          Definition DListQueue : Queue := {|
            t := (list nat) * (list nat);
            empty := (nil, nil);
            push x l :=
              let (back, front) := l in
              (x :: back,front);
            pop l :=
              let (back, front) := l in
              match front with
                | nil =>
                   match rev back with
                      | nil => None
                      | hd :: tl => Some (hd, (nil, tl))
                   end
                | hd :: tl => Some (hd, (back, tl))
              end
          |}.
      """) should generateScalaCode("""
      "trait Queue {
      "  type t
      "  def empty: t
      "  def push: Nat => t => t
      "  def pop: t => Option[(Nat, t)]
      "}
      "object ListQueue extends Queue {
      "  type t = List[Nat]
      "  def empty: t = Nil
      "  def push: Nat => t => t = (x: Nat) => (l: t) => x :: l
      "  def pop: t => Option[(Nat, t)] = (l: t) => rev(l) match {
      "    case Nil      => None
      "    case hd :: tl => Some((hd, rev(tl)))
      "  }
      "}
      "object DListQueue extends Queue {
      "  type t = (List[Nat], List[Nat])
      "  def empty: t = (Nil, Nil)
      "  def push: Nat => t => t = (x: Nat) => { (l: t) =>
      "    val (back, front) = l
      "    (x :: back, front)
      "  }
      "  def pop: t => Option[(Nat, t)] = { (l: t) =>
      "    val (back, front) = l
      "    front match {
      "      case Nil => rev(back) match {
      "        case Nil      => None
      "        case hd :: tl => Some((hd, (Nil, tl)))
      "      }
      "      case hd :: tl => Some((hd, (back, tl)))
      "    }
      "  }
      "}
      """)
  }

  test("""Testing Scala conversion of
          Require Import Coq.Lists.List.

          Record Queue := {
            t : Type;
            empty : t;
            push (x: nat) (l: t): t;
            pop (l: t): option (nat * t)
          }.

          Definition ListQueue : Queue := {|
            t := list nat;
            empty := nil;
            push a l := a :: l;
            pop l :=
              match rev l with
                | nil => None
                | hd :: tl => Some (hd, rev tl)
              end
          |}.
       """) {
    CoqParser("""
          Require Import Coq.Lists.List.

          Record Queue := {
            t : Type;
            empty : t;
            push (x: nat) (l: t): t;
            pop (l: t): option (nat * t)
          }.

          Definition ListQueue : Queue := {|
            t := list nat;
            empty := nil;
            push anotherName l := anotherName :: l;
            pop l :=
              match rev l with
                | nil => None
                | hd :: tl => Some (hd, rev tl)
              end
          |}.

      """) should generateScalaCode("""
      "trait Queue {
      "  type t
      "  def empty: t
      "  def push: Nat => t => t
      "  def pop: t => Option[(Nat, t)]
      "}
      "object ListQueue extends Queue {
      "  type t = List[Nat]
      "  def empty: t = Nil
      "  def push: Nat => t => t = (anotherName: Nat) => (l: t) => anotherName :: l
      "  def pop: t => Option[(Nat, t)] = (l: t) => rev(l) match {
      "    case Nil      => None
      "    case hd :: tl => Some((hd, rev(tl)))
      "  }
      "}
      """)
  }

  test("""Testing Scala conversion of
          Require Import Coq.Lists.List.

          Record TestRecord := {
            push (x y: nat) (l: list nat) : nat
          }.

          Definition RecordInstance : TestRecord := {|
            push a b l := a
          |}.
       """) {
    CoqParser("""
          Require Import Coq.Lists.List.

          Record TestRecord := Build_TestRecord {
            test (x y: nat) (l: list nat) : nat
          }.

          Definition RecordInstance : TestRecord := {|
            test a b l := a
          |}.

      """) should generateScalaCode(
      """
      "trait TestRecord {
      "  def test: Nat => Nat => List[Nat] => Nat
      "}
      "def Build_TestRecord(test: Nat => Nat => List[Nat] => Nat): TestRecord = {
      "  def TestRecord_test = test
      "  new TestRecord {
      "    def test: Nat => Nat => List[Nat] => Nat = TestRecord_test
      "  }
      "}
      "object RecordInstance extends TestRecord {
      "  def test: Nat => Nat => List[Nat] => Nat = (a: Nat) => (b: Nat) => (l: List[Nat]) => a
      "}
      """
    )
  }

  test("""Testing Scala conversion of
          Require Import Coq.Lists.List.

          Record Queue :=
          Build_Queue {
            T : Type;
            push : nat -> T -> T;
            pop : T -> option (nat * T);
            empty : T
          }.

          Arguments Build_Queue {T} _ _ _.

          Definition ListQueue := Build_Queue
            (fun (x : nat) (l : list nat) => x :: l)
            (fun l =>
              match rev l with
                | nil => None
                | hd :: tl => Some (hd, rev tl) end)
            nil
          .

          Definition DListQueue := Build_Queue
            (fun (x : nat) (l : (list nat) * (list nat)) =>
              let (back, front) := l in
              (x :: back,front))
            (fun l =>
              let (back, front) := l in
              match front with
                | nil =>
                   match rev back with
                      | nil => None
                      | hd :: tl => Some (hd, (nil, tl))
                   end
                | hd :: tl => Some (hd, (back, tl))
              end)
            (nil, nil)
          .
       """) {
    CoqParser("""
          Require Import Coq.Lists.List.

          Record Queue :=
          Build_Queue {
            T : Type;
            push : nat -> T -> T;
            pop : T -> option (nat * T);
            empty : T
          }.

          Arguments Build_Queue {T} _ _ _.

          Definition ListQueue := Build_Queue
            (fun (x : nat) (l : list nat) => x :: l)
            (fun l =>
              match rev l with
                | nil => None
                | hd :: tl => Some (hd, rev tl) end)
            nil
          .

          Definition DListQueue := Build_Queue
            (fun (x : nat) (l : (list nat) * (list nat)) =>
              let (back, front) := l in
              (x :: back,front))
            (fun l =>
              let (back, front) := l in
              match front with
                | nil =>
                   match rev back with
                      | nil => None
                      | hd :: tl => Some (hd, (nil, tl))
                   end
                | hd :: tl => Some (hd, (back, tl))
              end)
            (nil, nil)
          .
      """) should generateScalaCode(
      """
      "trait Queue {
      "  type T
      "  def push: Nat => T => T
      "  def pop: T => Option[(Nat, T)]
      "  def empty: T
      "}
      "def Build_Queue[T](push: Nat => T => T)(pop: T => Option[(Nat, T)])(empty: T): Queue = {
      "  type Queue_T = T
      "  def Queue_push = push
      "  def Queue_pop = pop
      "  def Queue_empty = empty
      "  new Queue {
      "    type T = Queue_T
      "    def push: Nat => T => T = Queue_push
      "    def pop: T => Option[(Nat, T)] = Queue_pop
      "    def empty: T = Queue_empty
      "  }
      "}
      "def ListQueue = Build_Queue((x: Nat) => (l: List[Nat]) => x :: l)(l => rev(l) match {
      "  case Nil      => None
      "  case hd :: tl => Some((hd, rev(tl)))
      "})(Nil)
      "def DListQueue = Build_Queue((x: Nat) => { (l: (List[Nat], List[Nat])) =>
      "  val (back, front) = l
      "  (x :: back, front)
      "})({ l =>
      "  val (back, front) = l
      "  front match {
      "    case Nil => rev(back) match {
      "      case Nil      => None
      "      case hd :: tl => Some((hd, (Nil, tl)))
      "    }
      "    case hd :: tl => Some((hd, (back, tl)))
      "  }
      "})((Nil, Nil))
      """
    )
  }

  test("""Testing Scala conversion of
          Require Import Coq.Lists.List.

          Record Queue :=
          Build_Queue {
            T : Type;
            empty : T;
            push : nat -> T -> T;
            pop : T -> option (nat * T)
          }.

          Definition ListQueue := Build_Queue
            (list nat)
            nil
            (fun (x : nat) (l : list nat) => x :: l)
            (fun l =>
              match rev l with
                | nil => None
                | hd :: tl => Some (hd, rev tl) end)
          .

          Definition DListQueue := Build_Queue
            ((list nat) * (list nat))
            (nil, nil)
            (fun (x : nat) (l : (list nat) * (list nat)) =>
              let (back, front) := l in
              (x :: back,front))
            (fun l =>
              let (back, front) := l in
              match front with
                | nil =>
                   match rev back with
                      | nil => None
                      | hd :: tl => Some (hd, (nil, tl))
                   end
                | hd :: tl => Some (hd, (back, tl))
              end)
          .
       """) {
    CoqParser("""
          Require Import Coq.Lists.List.

          Record Queue :=
          Build_Queue {
            T : Type;
            empty : T;
            push : nat -> T -> T;
            pop : T -> option (nat * T)
          }.

          Definition ListQueue := Build_Queue
            (list nat)
            nil
            (fun (x : nat) (l : list nat) => x :: l)
            (fun l =>
              match rev l with
                | nil => None
                | hd :: tl => Some (hd, rev tl) end)
          .

          Definition DListQueue := Build_Queue
            ((list nat) * (list nat))
            (nil, nil)
            (fun (x : nat) (l : (list nat) * (list nat)) =>
              let (back, front) := l in
              (x :: back,front))
            (fun l =>
              let (back, front) := l in
              match front with
                | nil =>
                   match rev back with
                      | nil => None
                      | hd :: tl => Some (hd, (nil, tl))
                   end
                | hd :: tl => Some (hd, (back, tl))
              end)
          .
      """) should generateScalaCode(
      """
      "trait Queue {
      "  type T
      "  def empty: T
      "  def push: Nat => T => T
      "  def pop: T => Option[(Nat, T)]
      "}
      "def Build_Queue[T](empty: T)(push: Nat => T => T)(pop: T => Option[(Nat, T)]): Queue = {
      "  type Queue_T = T
      "  def Queue_empty = empty
      "  def Queue_push = push
      "  def Queue_pop = pop
      "  new Queue {
      "    type T = Queue_T
      "    def empty: T = Queue_empty
      "    def push: Nat => T => T = Queue_push
      "    def pop: T => Option[(Nat, T)] = Queue_pop
      "  }
      "}
      "def ListQueue = Build_Queue[List[Nat]](Nil)((x: Nat) => (l: List[Nat]) => x :: l)(l => rev(l) match {
      "  case Nil      => None
      "  case hd :: tl => Some((hd, rev(tl)))
      "})
      "def DListQueue = Build_Queue[(List[Nat], List[Nat])]((Nil, Nil))((x: Nat) => { (l: (List[Nat], List[Nat])) =>
      "  val (back, front) = l
      "  (x :: back, front)
      "})({ l =>
      "  val (back, front) = l
      "  front match {
      "    case Nil => rev(back) match {
      "      case Nil      => None
      "      case hd :: tl => Some((hd, (Nil, tl)))
      "    }
      "    case hd :: tl => Some((hd, (back, tl)))
      "  }
      "})
      """
    )
  }

  test("""Testing Scala conversion of
          Require Import Coq.Lists.List.

          Record Queue :=
          newQueue {
            T : Type;
            empty : T;
            push (x : nat) (q : T) : T;
            pop (q: T) : option (nat * T)
          }.

          Definition ListQueue := newQueue
            (list nat)
            nil
            (fun (x : nat) (l : list nat) => x :: l)
            (fun l =>
              match rev l with
                | nil => None
                | hd :: tl => Some (hd, rev tl) end)
          .

          Definition DListQueue := newQueue
            ((list nat) * (list nat))
            (nil, nil)
            (fun (x : nat) (l : (list nat) * (list nat)) =>
              let (back, front) := l in
              (x :: back,front))
            (fun l =>
              let (back, front) := l in
              match front with
                | nil =>
                   match rev back with
                      | nil => None
                      | hd :: tl => Some (hd, (nil, tl))
                   end
                | hd :: tl => Some (hd, (back, tl))
              end)
          .
       """) {
    CoqParser("""
          Require Import Coq.Lists.List.

          Record Queue :=
          newQueue {
            T : Type;
            empty : T;
            push (x : nat) (q : T) : T;
            pop (q: T) : option (nat * T)
          }.

          Definition ListQueue := newQueue
            (list nat)
            nil
            (fun (x : nat) (l : list nat) => x :: l)
            (fun l =>
              match rev l with
                | nil => None
                | hd :: tl => Some (hd, rev tl) end)
          .

          Definition DListQueue := newQueue
            ((list nat) * (list nat))
            (nil, nil)
            (fun (x : nat) (l : (list nat) * (list nat)) =>
              let (back, front) := l in
              (x :: back,front))
            (fun l =>
              let (back, front) := l in
              match front with
                | nil =>
                   match rev back with
                      | nil => None
                      | hd :: tl => Some (hd, (nil, tl))
                   end
                | hd :: tl => Some (hd, (back, tl))
              end)
          .
      """) should generateScalaCode(
      """
      "trait Queue {
      "  type T
      "  def empty: T
      "  def push: Nat => T => T
      "  def pop: T => Option[(Nat, T)]
      "}
      "def newQueue[T](empty: T)(push: Nat => T => T)(pop: T => Option[(Nat, T)]): Queue = {
      "  type Queue_T = T
      "  def Queue_empty = empty
      "  def Queue_push = push
      "  def Queue_pop = pop
      "  new Queue {
      "    type T = Queue_T
      "    def empty: T = Queue_empty
      "    def push: Nat => T => T = Queue_push
      "    def pop: T => Option[(Nat, T)] = Queue_pop
      "  }
      "}
      "def ListQueue = newQueue[List[Nat]](Nil)((x: Nat) => (l: List[Nat]) => x :: l)(l => rev(l) match {
      "  case Nil      => None
      "  case hd :: tl => Some((hd, rev(tl)))
      "})
      "def DListQueue = newQueue[(List[Nat], List[Nat])]((Nil, Nil))((x: Nat) => { (l: (List[Nat], List[Nat])) =>
      "  val (back, front) = l
      "  (x :: back, front)
      "})({ l =>
      "  val (back, front) = l
      "  front match {
      "    case Nil => rev(back) match {
      "      case Nil      => None
      "      case hd :: tl => Some((hd, (Nil, tl)))
      "    }
      "    case hd :: tl => Some((hd, (back, tl)))
      "  }
      "})
      """
    )
  }

  test("""Testing Scala conversion of
          Require Import Coq.Lists.List.

          Record Queue := {
            T : Type;
            empty : T;
            push : nat -> T -> T;
            pop : T -> option (nat * T)
          }.

          Definition ListQueue : Queue := {|
            T := list nat;
            empty := nil;
            push := fun x l => x :: l;
            pop := fun l =>
              match rev l with
                | nil => None
                | hd :: tl => Some (hd, rev tl) end
          |}.

          Definition DListQueue : Queue := {|
            T := (list nat) * (list nat);
            empty := (nil, nil);
            push := fun x l =>
              let (back, front) := l in
              (x :: back,front);
            pop := fun l =>
              let (back, front) := l in
              match front with
                | nil =>
                   match rev back with
                      | nil => None
                      | hd :: tl => Some (hd, (nil, tl))
                   end
                | hd :: tl => Some (hd, (back, tl))
              end
          |}.
       """) {
    CoqParser("""
          Require Import Coq.Lists.List.

          Record Queue := {
            T : Type;
            empty : T;
            push : nat -> T -> T;
            pop : T -> option (nat * T)
          }.

          Definition ListQueue : Queue := {|
            T := list nat;
            empty := nil;
            push := fun x l => x :: l;
            pop := fun l =>
              match rev l with
                | nil => None
                | hd :: tl => Some (hd, rev tl) end
          |}.

          Definition DListQueue : Queue := {|
            T := (list nat) * (list nat);
            empty := (nil, nil);
            push := fun x l =>
              let (back, front) := l in
              (x :: back,front);
            pop := fun l =>
              let (back, front) := l in
              match front with
                | nil =>
                   match rev back with
                      | nil => None
                      | hd :: tl => Some (hd, (nil, tl))
                   end
                | hd :: tl => Some (hd, (back, tl))
              end
          |}.
      """) should generateScalaCode("""
      "trait Queue {
      "  type T
      "  def empty: T
      "  def push: Nat => T => T
      "  def pop: T => Option[(Nat, T)]
      "}
      "object ListQueue extends Queue {
      "  type T = List[Nat]
      "  def empty: T = Nil
      "  def push: Nat => T => T = x => l => x :: l
      "  def pop: T => Option[(Nat, T)] = l => rev(l) match {
      "    case Nil      => None
      "    case hd :: tl => Some((hd, rev(tl)))
      "  }
      "}
      "object DListQueue extends Queue {
      "  type T = (List[Nat], List[Nat])
      "  def empty: T = (Nil, Nil)
      "  def push: Nat => T => T = x => { l =>
      "    val (back, front) = l
      "    (x :: back, front)
      "  }
      "  def pop: T => Option[(Nat, T)] = { l =>
      "    val (back, front) = l
      "    front match {
      "      case Nil => rev(back) match {
      "        case Nil      => None
      "        case hd :: tl => Some((hd, (Nil, tl)))
      "      }
      "      case hd :: tl => Some((hd, (back, tl)))
      "    }
      "  }
      "}
      """)
  }

  test("""Testing Scala conversion of Essence of DOT List example
      Inductive List {A : Set} := newList {
        isEmpty: bool;
        head: A;
        tail: List A
      }.

      Arguments newList {A} _ _ _.

      Definition cons {A: Set} (hd: A) (tl: (@ List A)) := newList true hd tl.
       """) {
    CoqParser("""
      Inductive List {A : Set} := newList {
        isEmpty: bool;
        head: A;
        tail: List A
      }.

      Arguments newList {A} _ _ _.

      Definition cons {A: Set} (hd: A) (tl: (@ List A)) := newList true hd tl.
      """) should generateScalaCode("""
      "trait List[A] {
      "  def isEmpty: Boolean
      "  def head: A
      "  def tail: List[A]
      "}
      "def newList[A](isEmpty: Boolean)(head: A)(tail: List[A]): List[A] = {
      "  def List_isEmpty = isEmpty
      "  def List_head = head
      "  def List_tail = tail
      "  new List[A] {
      "    def isEmpty: Boolean = List_isEmpty
      "    def head: A = List_head
      "    def tail: List[A] = List_tail
      "  }
      "}
      "def cons[A](hd: A)(tl: List[A]) = newList(true)(hd)(tl)
      """)
  }

  test("""Testing Scala conversion of Essence of DOT List example
      Inductive List := {
        A : Type;
        isEmpty: bool;
        head: A;
        tail: List
      }.

      Definition cons {B: Type} (hd: B) (tl: List) : List := {|
        A := B;
        isEmpty := true;
        head := hd;
        tail := tl
      |}.
       """) {
    CoqParser("""
      Inductive List := {
        A : Type;
        isEmpty: bool;
        head: A;
        tail: List
      }.

      Definition cons {B: Type} (hd: B) (tl: List) : List := {|
        A := B;
        isEmpty := true;
        head := hd;
        tail := tl
      |}.
      """) should generateScalaCode("""
      "trait List {
      "  type A
      "  def isEmpty: Boolean
      "  def head: A
      "  def tail: List
      "}
      "def cons[B](hd: B)(tl: List): List = new List {
      "  type A = B
      "  def isEmpty: Boolean = true
      "  def head: A = hd
      "  def tail: List = tl
      "}
      """)
  }

  test("""Testing Scala conversion of Essence of DOT List example
      Inductive MyList := {
        A : Type;
        isEmpty: bool;
        head: option A;
        tail: option MyList
      }.

      Definition MyCons {B: Set} (hd: B) (tl: MyList) : MyList := {|
        A := B;
        isEmpty := true;
        head := Some hd;
        tail := Some tl
      |}.

      Definition MyNil {B: Set} : MyList := {|
        A := B;
        isEmpty := false;
        head := None;
        tail := None
      |}.
       """) {
    CoqParser("""
      Inductive MyList := {
        A : Type;
        isEmpty: bool;
        head: option A;
        tail: option MyList
      }.

      Definition MyCons {B: Set} (hd: B) (tl: MyList) : MyList := {|
        A := B;
        isEmpty := true;
        head := Some hd;
        tail := Some tl
      |}.

      Definition MyNil {B: Set} : MyList := {|
        A := B;
        isEmpty := false;
        head := None;
        tail := None
      |}.
      """) should generateScalaCode("""
      "trait MyList {
      "  type A
      "  def isEmpty: Boolean
      "  def head: Option[A]
      "  def tail: Option[MyList]
      "}
      "def MyCons[B](hd: B)(tl: MyList): MyList = new MyList {
      "  type A = B
      "  def isEmpty: Boolean = true
      "  def head: Option[A] = Some(hd)
      "  def tail: Option[MyList] = Some(tl)
      "}
      "def MyNil[B]: MyList = new MyList {
      "  type A = B
      "  def isEmpty: Boolean = false
      "  def head: Option[A] = None
      "  def tail: Option[MyList] = None
      "}
      """)
  }

  test("""Testing Scala conversion of
        Require Import List.

        Record Queue := {
          t : Type;
          empty : t;
          push : nat -> t -> t;
          pop : t -> option (nat * t)
        }.

        Definition ListQueue : Queue := {|
          t := list nat;
          empty := nil;
          push := fun x l => x :: l;
          pop := fun l =>
            match rev l with
              | nil => None
              | hd :: tl => Some (hd, rev tl) end
        |}.

        Definition DListQueue : Queue := {|
          t := (list nat) * (list nat);
          empty := (nil, nil);
          push := fun x l =>
            let (back, front) := l in
            (x :: back,front);
          pop := fun l =>
            let (back, front) := l in
            match front with
              | nil =>
                 match rev back with
                    | nil => None
                    | hd :: tl => Some (hd, (nil, tl))
                 end
              | hd :: tl => Some (hd, (back, tl))
            end
        |}.

        Definition bind_option {A B}
        (x : option A)
        (f : A -> option B) : option B :=
          match x with
           | Some x => f x
           | None => None
          end.

        Definition bind_option2 {A B C}
        (x : option (A * B))
        (f : A -> B -> option C) : option C :=
        bind_option x
          (fun (yz : A * B) =>
           let (y, z) := yz in f y z).

        Definition option_map {A B}
        (o : option A) (f : A -> B)
        : option B :=
          match o with
            | Some a => Some (f a)
            | None => None
          end.

        Fixpoint nat_rect {P : Type}
          (op : nat -> P -> P) (n : nat) (x : P) : P :=
          match n with
          | 0 => x
          | S n0 => op n0 (nat_rect op n0 x)
          end.

        Definition sumElems(Q : Queue)(q0: option Q.(t)) : option Q.(t) :=
        bind_option q0
        (fun (q1 : Q.(t)) =>
         let x_q1 := Q.(pop) q1
         in
         bind_option2 x_q1
          (fun (x : nat) (q2 : Q.(t)) =>
           let y_q3 := Q.(pop) q2
           in
           bind_option2 y_q3
            (fun (y : nat) (q3 : Q.(t)) =>
              let sum := x + y
              in Some (Q.(push) sum q3)
            )
          )
        )
        .

        Definition program (Q : Queue) (n : nat) : option nat :=
        (* q := 0::1::2::...::n *)
        let q : Q.(t) :=
          nat_rect Q.(push) (S n) Q.(empty)
        in
        let q0 : option Q.(t) :=
          nat_rect
            (fun _ (q0: option Q.(t)) => sumElems Q q0)
            n
            (Some q)
        in
        bind_option q0
          (fun (q1 : Q.(t)) => option_map (Q.(pop) q1) fst)
        .
       """) {
    CoqParser("""
        Require Import List.

        Record Queue := {
          t : Type;
          empty : t;
          push : nat -> t -> t;
          pop : t -> option (nat * t)
        }.

        Definition ListQueue : Queue := {|
          t := list nat;
          empty := nil;
          push := fun x l => x :: l;
          pop := fun l =>
            match rev l with
              | nil => None
              | hd :: tl => Some (hd, rev tl) end
        |}.

        Definition DListQueue : Queue := {|
          t := (list nat) * (list nat);
          empty := (nil, nil);
          push := fun x l =>
            let (back, front) := l in
            (x :: back,front);
          pop := fun l =>
            let (back, front) := l in
            match front with
              | nil =>
                 match rev back with
                    | nil => None
                    | hd :: tl => Some (hd, (nil, tl))
                 end
              | hd :: tl => Some (hd, (back, tl))
            end
        |}.

        Definition bind_option {A B}
        (x : option A)
        (f : A -> option B) : option B :=
          match x with
           | Some x => f x
           | None => None
          end.

        Definition bind_option2 {A B C}
        (x : option (A * B))
        (f : A -> B -> option C) : option C :=
        bind_option x
          (fun (yz : A * B) =>
           let (y, z) := yz in f y z).

        Definition option_map {A B}
        (o : option A) (f : A -> B)
        : option B :=
          match o with
            | Some a => Some (f a)
            | None => None
          end.

        Fixpoint nat_rect {P : Type}
          (op : nat -> P -> P) (n : nat) (x : P) : P :=
          match n with
          | 0 => x
          | S n0 => op n0 (nat_rect op n0 x)
          end.

        Definition sumElems(Q : Queue)(q0: option Q.(t)) : option Q.(t) :=
        bind_option q0
        (fun (q1 : Q.(t)) =>
         let x_q1 := Q.(pop) q1
         in
         bind_option2 x_q1
          (fun (x : nat) (q2 : Q.(t)) =>
           let y_q3 := Q.(pop) q2
           in
           bind_option2 y_q3
            (fun (y : nat) (q3 : Q.(t)) =>
              let sum := x + y
              in Some (Q.(push) sum q3)
            )
          )
        )
        .

        Definition program (Q : Queue) (n : nat) : option nat :=
        (* q := 0::1::2::...::n *)
        let q : Q.(t) :=
          nat_rect Q.(push) (S n) Q.(empty)
        in
        let q0 : option Q.(t) :=
          nat_rect
            (fun _ (q0: option Q.(t)) => sumElems Q q0)
            n
            (Some q)
        in
        bind_option q0
          (fun (q1 : Q.(t)) => option_map (Q.(pop) q1) fst)
        .
      """) should generateScalaCode(
      """
      "trait Queue {
      "  type t
      "  def empty: t
      "  def push: Nat => t => t
      "  def pop: t => Option[(Nat, t)]
      "}
      "object ListQueue extends Queue {
      "  type t = List[Nat]
      "  def empty: t = Nil
      "  def push: Nat => t => t = x => l => x :: l
      "  def pop: t => Option[(Nat, t)] = l => rev(l) match {
      "    case Nil      => None
      "    case hd :: tl => Some((hd, rev(tl)))
      "  }
      "}
      "object DListQueue extends Queue {
      "  type t = (List[Nat], List[Nat])
      "  def empty: t = (Nil, Nil)
      "  def push: Nat => t => t = x => { l =>
      "    val (back, front) = l
      "    (x :: back, front)
      "  }
      "  def pop: t => Option[(Nat, t)] = { l =>
      "    val (back, front) = l
      "    front match {
      "      case Nil => rev(back) match {
      "        case Nil      => None
      "        case hd :: tl => Some((hd, (Nil, tl)))
      "      }
      "      case hd :: tl => Some((hd, (back, tl)))
      "    }
      "  }
      "}
      "def bind_option[A, B](x: Option[A])(f: A => Option[B]): Option[B] =
      "  x match {
      "    case Some(x) => f(x)
      "    case None    => None
      "  }
      "def bind_option2[A, B, C](x: Option[(A, B)])(f: A => B => Option[C]): Option[C] = bind_option(x)({ (yz: (A, B)) =>
      "  val (y, z) = yz
      "  f(y)(z)
      "})
      "def option_map[A, B](o: Option[A])(f: A => B): Option[B] =
      "  o match {
      "    case Some(a) => Some(f(a))
      "    case None    => None
      "  }
      "def nat_rect[P](op: Nat => P => P)(n: Nat)(x: P): P =
      "  n match {
      "    case Zero  => x
      "    case S(n0) => op(n0)(nat_rect(op)(n0)(x))
      "  }
      "def sumElems(Q: Queue)(q0: Option[Q.t]): Option[Q.t] = bind_option(q0)({ (q1: Q.t) =>
      "  val x_q1 = Q.pop(q1)
      "  bind_option2(x_q1)((x: Nat) => { (q2: Q.t) =>
      "    val y_q3 = Q.pop(q2)
      "    bind_option2(y_q3)((y: Nat) => { (q3: Q.t) =>
      "      val sum = x + y
      "      Some(Q.push(sum)(q3))
      "    })
      "  })
      "})
      "def program(Q: Queue)(n: Nat): Option[Nat] = {
      "  val q: Q.t = nat_rect(Q.push)(S(n))(Q.empty)
      "  val q0: Option[Q.t] = nat_rect(_ => (q0: Option[Q.t]) => sumElems(Q)(q0))(n)(Some(q))
      "  bind_option(q0)((q1: Q.t) => option_map(Q.pop(q1))(fst))
      "}
      """
    )

  }
}
