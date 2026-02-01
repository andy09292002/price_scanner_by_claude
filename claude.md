# Agent Instructions

## MVC framework

**Model Layer**
- files are under '\src\models'
- Objects are used for data access and 1-to-1 mapping with database tables
- act as ORM (Object Relational Mapping)
- Support CUID actions

**View**
- files are under '\src\views'
- stored all UI interface like jsp

**Controller**
- files are under '\src\controllers'
- Provide API endpoints, handle direct API requests or UI requests from view layer
- include all business logic and validation
- call model layer to access database
- return data to view layer

**Other static files**
- files are under '\resources' like other java projects

## Architecture

** Framework**
- Spring Boot  
- Spring MVC
- Hibernate
- Mongodb
- JPA
- Maven
- Junit
- Log4j
- Lombok
- Swagger

## Unit Test
- Using JUnit to perform unit test
- for each controller, need to create one test class, which includes all test cases such as edge cases, happy case, fail cases.
- test cases are stored under '\src\test\java'
- test cases are annotated with @Test
- test cases are executed by maven command 'mvn test'
- test coverage is 80%



