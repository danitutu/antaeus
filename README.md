## Thoughts, reasoning and opinions

- `PaymentProvider` is calling an external service and shouldn't be touched
- `BillingService` is the class responsible for charging customers invoices
- `BillingService` is processing only the `PENDING` invoices
- `BillingService` is will charge each customer on a separate coroutine. This is because I wanted to improve the execution time since calls to external apps could take a significant amount of time. The reason for processing one customer on a single coroutine (charging customer for each invoice sequentially) helps avoiding issues with customer balance (a customer can have multiple pending invoices)
- For simplicity, I supposed that invoice IDs are always unique and we can never have collisions (even in case we would run multiple instances of this service where an UUID would be a better choice)
- I didn't see a need to make use of transactions. If there is a failure in the charging process then the transaction will not be marked as PAID.
- If a customer has multiple invoices to be charged and, when charging them, the charging from the external system returns `false`, then other invoices will be processed for that particular customer (this means that there are issues with customer account ballance). Additionaly we can set an alert so when this happens we are notified. This might be a case that requires manual investigation.
- As a scheduler I decided to use `Quartz`
- For simplicity and in regard to `Quartz`, I considered that this system is running in a single instance. In case we want to run multiple instances of this application, then we need to enable the clustering feature from `Quartz`. I've never used this feature before but it seems to enable a mechanism for leader election. More details at http://www.quartz-scheduler.org/documentation/quartz-1.8.6/configuration/ConfigJDBCJobStoreClustering.html
- If we have K8S, we could make use a K8S job and extract the billing logic. This way we make sure the job is run by a single instance at a single moment in time.
- For simplicity, I decided not to add a retry mechanism when there is a network error received from the `paymentProvider.charge()`
- Assumption: I supposed that all unpaid invoices are to be processed in the first day of each month
- The `BillingService` is using `Dispatchers.IO` as default dispatcher since it is also making remote calls. This can be easily swapped with another `CouroutineDispatcher` since it is provided as a constructor parameter to the class. It might make sense to create a different dispatcher for this job so that it won't impact other processes that make use of `Dispatchers.IO`, but I kept it this way for simplicity reasons.
- So far I added only unit tests. There's definitely a need for integration tests but, for achieving that, I would've need more time for setting them up.
- The integration between Scheduler and the other parts of the application is done at the app module level. To me it makes sense to be there or probably in a separate scheduling module. The job shouldn't be part of the app core.
- `Quartz` runs tasks in its own ThreadPool (I think by default it has 8 threads). Blocking one thread in this case doesn't seem to be a problem.
- For error handling/management I used `ArrowKT` `Either` type class. This way of doing error handling brings lots of great benefits. 
- Updated Kotlin to the newest version so I can benefit from sealed interfaces and sealed classes
- Making the cron job configurable through env vars

The task took around 8 hours spread across multiple days.

Because of the mocked implementation which randomly returns either success or failure, the job should be let to execute multiple times. To speed things up, you can provide `"0 1/1 * * * ?"` job cron expression. This will execute the job every 1 minute. 

Cron patterns:
- every minute: `0 1/1 * * * ?`
- start of every month: `0 0 0 1 1/1 ? *`

Run with:
```
docker build -t antaeus .
docker run -p 7000:7000 -e BILLING_JOB_CRON="0 0 0 1 1/1 ? *" antaeus
```

With the exposed port, after each job invocation, you can verify the invoices changes using `curl http://localhost:7000/rest/v1/invoices`.

## Antaeus

Antaeus (/ænˈtiːəs/), in Greek mythology, a giant of Libya, the son of the sea god Poseidon and the Earth goddess Gaia. He compelled all strangers who were passing through the country to wrestle with him. Whenever Antaeus touched the Earth (his mother), his strength was renewed, so that even if thrown to the ground, he was invincible. Heracles, in combat with him, discovered the source of his strength and, lifting him up from Earth, crushed him to death.

Welcome to our challenge.

## The challenge

As most "Software as a Service" (SaaS) companies, Pleo needs to charge a subscription fee every month. Our database contains a few invoices for the different markets in which we operate. Your task is to build the logic that will schedule payment of those invoices on the first of the month. While this may seem simple, there is space for some decisions to be taken and you will be expected to justify them.

## Instructions

Fork this repo with your solution. Ideally, we'd like to see your progression through commits, and don't forget to update the README.md to explain your thought process.

Please let us know how long the challenge takes you. We're not looking for how speedy or lengthy you are. It's just really to give us a clearer idea of what you've produced in the time you decided to take. Feel free to go as big or as small as you want.

## Developing

Requirements:
- \>= Java 11 environment

Open the project using your favorite text editor. If you are using IntelliJ, you can open the `build.gradle.kts` file and it is gonna setup the project in the IDE for you.

### Building

```
./gradlew build
```

### Running

There are 2 options for running Anteus. You either need libsqlite3 or docker. Docker is easier but requires some docker knowledge. We do recommend docker though.

*Running Natively*

Native java with sqlite (requires libsqlite3):

If you use homebrew on MacOS `brew install sqlite`.

```
./gradlew run
```

*Running through docker*

Install docker for your platform

```
docker build -t antaeus .
docker run antaeus
```

### App Structure
The code given is structured as follows. Feel free however to modify the structure to fit your needs.
```
├── buildSrc
|  | gradle build scripts and project wide dependency declarations
|  └ src/main/kotlin/utils.kt 
|      Dependencies
|
├── pleo-antaeus-app
|       main() & initialization
|
├── pleo-antaeus-core
|       This is probably where you will introduce most of your new code.
|       Pay attention to the PaymentProvider and BillingService class.
|
├── pleo-antaeus-data
|       Module interfacing with the database. Contains the database 
|       models, mappings and access layer.
|
├── pleo-antaeus-models
|       Definition of the Internal and API models used throughout the
|       application.
|
└── pleo-antaeus-rest
        Entry point for HTTP REST API. This is where the routes are defined.
```

### Main Libraries and dependencies
* [Exposed](https://github.com/JetBrains/Exposed) - DSL for type-safe SQL
* [Javalin](https://javalin.io/) - Simple web framework (for REST)
* [kotlin-logging](https://github.com/MicroUtils/kotlin-logging) - Simple logging framework for Kotlin
* [JUnit 5](https://junit.org/junit5/) - Testing framework
* [Mockk](https://mockk.io/) - Mocking library
* [Sqlite3](https://sqlite.org/index.html) - Database storage engine

Happy hacking 😁!
