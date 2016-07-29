import org.junit.runner.RunWith
import org.scalatest.FunSpec
import org.scalatest.junit.JUnitRunner
import bots.SwarmBot.SwarmPattern
import bots.StockBot.TickerPattern

@RunWith(classOf[JUnitRunner])
class TestPatterns extends FunSpec {
  describe("the swarmbot") {
    List("release the bees", "unleash the bats", "RELEASE THE BATS", "release the crabs")
      .foreach(s => {
        it("question: " + s) {
          s match {
            case SwarmPattern(_, s2) => println(s2)
            case _ => fail("did not match: " + s)
          }
        }
      })
  }

  describe("the stockbot") {
    List("$BWLD", "$FSLR", "$LMT:NYSE")
      .foreach(s => {
        it("stock: " + s) {
          s match {
            case TickerPattern(s2,a) => println(s"tick=$s2 ex=$a")
            case _ => fail("did not match: " + s)
          }
        }
      })
  }
}