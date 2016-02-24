import org.junit.runner.RunWith
import org.scalatest._
import org.scalatest.junit.JUnitRunner

@RunWith(classOf[JUnitRunner])
class TestBeerOclock extends FunSpec {

  val Question = """(?i)(.*is.*it.*beer.*o'?clock.*)""".r
  val Answer = """(?i)(.*(?:it's|its|it is).*beer.*o'?clock.*)""".r

  val answers = Seq(
    """   it's beer oclock zomg!!!!111!!111one11! """,
    """ it is totally beer o'clock, bro""",
    """its beer oclock""",
    """it is beer oclock"""
  )

  val questions = Seq (
    """ is it beer oclock yet?  """,
    """is it beer o'clock?""",
    """ is it beer oclock yet  """,
    """ is it beer o'clock"""
  )

  describe("beer o'clock") {

    questions
      .flatMap(q => Seq(q, q.toUpperCase()))
      .foreach(string =>
        it("question: " + string) {
          string match {
            case Answer(s1, s2) => fail(string)
            case Answer(s1) => fail(string)
            case Question(s) => println(s)
          }
        })

    answers
      .flatMap(q => Seq(q, q.toUpperCase()))
      .foreach(string =>
        it("answer: " + string) {
          string match {
            case Answer(s) => println("answer 1: " + s)
            case Answer(s1, s2) => fail(string)
            case Question(s) => fail(s)
          }
        })
  }
}