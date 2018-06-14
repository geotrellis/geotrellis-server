package geotrellis.server.source

import cats._


//case class Source[A, B](fn: A => B)

//object Source {
//  implicit def applicativeForEither: Applicative[Source[Input, ?]] = new Applicative[Source[?, ?]] {
//    def product[A, B](fa: Source[Input, A], fb: Either[Input, B]): Source[(Input, Input), (A, B)] = (fa, fb) match {
//      case (Right(a), Right(b)) => Right((a, b))
//      case (Left(l) , _       ) => Left(l)
//      case (_       , Left(l) ) => Left(l)
//    }

//    def pure[A](a: A): Either[L, A] = Right(a)

//    def map[A, B](fa: Either[L, A])(f: A => B): Either[L, B] = fa match {
//      case Right(a) => Right(f(a))
//      case Left(l)  => Left(l)
//    }
//  }

//  implicit def optionMonad(implicit app: Applicative[Source]) =
//    new Monad[Source] {

//      override def flatMap[A, B](fa: Option[A])(f: A => Option[B]): Option[B] =
//        app.map(fa)(f).flatten
//      // Reuse this definition from Applicative.
//      override def pure[A](a: A): Option[A] = app.pure(a)

//      @annotation.tailrec
//      def tailRecM[A, B](init: A)(fn: A => Option[Either[A, B]]): Option[B] =
//        fn(init) match {
//          case None => None
//          case Some(Right(b)) => Some(b)
//          case Some(Left(a)) => tailRecM(a)(fn)
//        }
//    }
//}
