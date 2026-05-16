package app.luci.finance

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication

@SpringBootApplication
class FinanceApiApplication

fun main(args: Array<String>) {
    runApplication<FinanceApiApplication>(*args)
}
