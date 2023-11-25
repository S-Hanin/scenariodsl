# Scenario lib

### Problem

The problem is next:  
We have spring boot web application, 3 tiers - controller -> service -> repository.
Let's say some kind of system to process documents.  
Every endpoint includes a bunch of operations and integrations with other services.
And besides that you have scenarios for every endpoint that you would like to keep readable in your code.
I mean a scenario like this one:
1. find document by `request.docId`
   1. if document has status `DRAFT` respond with http code 200 and a message "approving a document with DRAFT status is forbidden"
   1. check some other conditions, if something wrong - respond with http code 200 and a message "something wrong ..."
1. query the service B/documents/register
   1. if response has `errorData` with code `"01"` - respond with http code 200 and a message "document already registered"
   1. if response has `errorData` with code `"02"` - respond with http code 200 and a message "something wrong, who knows what"  

and so on ...

So, this is a scenario, some steps within, validation on each step...
Let's imagine, that we have services which implement some basic actions like `DocumentService.getDocument`, `BServiceAdapter.registerDocument`. Services throw theirs own exceptions, every scenario has its own.  

Straight way to implement this scenario would be something like this:  
```java
try {
       var document = documentService.getDocument(request.getDocId);
       
       if (document.getStatus() == Status.DRAFT) {
           return new GetDocumentRs()
               .errorData(new ErrorData()
                   .code("02")
                   .name("Document is in DRAFT status"))
       }
       
       var bResponse = bServiceAdapter.register(document);
       if (bResponse.getErrorData() != null) {
           var errorData = bResponse.getErrorData();
           var response = switch (errorData.getCode()) {
               case "01" -> new GetDocumentRs()
                   .errorData(new ErrorData().code("03").name("document already registered"));
               case "02" -> new GetDocumentRs()
                   .errorData(new ErrorData().code("04").name("something wrong, who knows what"));
               }
           }
       
       return new GetDocumentRs()
          .document(documentMapper.toResponseModel(document));
    } catch (ServiceException ex) {
	   return new new GetDocumentRs()
            .errorData(new ErrorData().code("05").name("document not found"));
    } catch (IntegrationException ex) {
	   return new new GetDocumentRs()
            .errorData(new ErrorData().code("06").name("can't register document. unknown error"));
    }
```
Usually the place for this code is a method of a service and there could be quite a mess because
generally a service has several methods.

It's a terrible code, but, to be honest it's just a first try, of course it should be refactored and so on.
So I'd like exceptions to be caught somewhere else, not in this method, I'd like this method to be clean and readable, and 
I'd be able to read the scenario I was given and compare it to code I wrote.

### Solution
Let's add one more tier between `controller` and `service`:  
`controller` -> `scenario` -> `service` -> `repository`



I'd like it would look like this:
```java
var document = step(() -> documentService.getDocument(request.getDocId()), // service can throw exception and it will be intercepted
                    () -> new ScenarioException(Error.DOCUMENT_NOT_FOUND)) // we'll throw our own exception instead
         .validate(it -> !hasStatus(it, Status.DRAFT), 
                   () -> new ScenarioException(Error.STATUS_IS_DRAFT))
         .get();

var bResponse = step(() -> bServiceAdapter.register(document)) // don't throw exception if occured one
         .onError(IntegrationException.class,
                  ex -> raise(switch(ex.getErrorData().getCode())) {
	                    case "01" -> new ScenarioException(Error.DOCUMENT_ALREADY_REGISTERED);
                            default -> new ScenarioException(Error.SOMETHING_WRONG);
                  })
         .onError(() -> raise(new ScenarioException(Error.UNKNOWN_ERROR)))
         .get();

return new GetDocumentRs()
    .document(documentMapper.toResponseModel(document));

```
Did some kind of refactoring... Scenario is more formalized here: we have steps, validations, error handling.
The code is readable, and it's possible to read it and compare to scenario.

### Summing up:
- we have services which return result or throw error
- we have scenario, which handle service exceptions and throw its own
- we have ControllerAdvice to handle scenario exceptions
- we escaped from using try ... catch and if ... else blocks
- controversial, but looks more readable
- allows to read the scenario and compare to code almost line by line


## Use cases

### Runnable

Execute code that does not return anything.

```java
import static ru.khanin.scenario.Scenario.Static.step;


// exception will be swallowed 
step(() -> throwSomeError());

// exception will be caught and substituted
step(
	() -> throwSomeError(),
	() -> new ScenarioException());
```

### Supplier
Execute some code that returns a result.  
Validate code result, throw an error if validation failed:
```java
String result = step(() -> "Hello world")
	.validate(
		it -> it.equals("Hello world"),
		() -> new ScenatioException())
	.get();

```

Run some side effect action: 
```java
String result = step(() -> "Hello world")
	.apply(it -> log.debug(it))
	.get()
```

Map result:
```java
Integer result = step(() -> "Hello world")
	.map(String::length)
	.get()
```

### Conditional execution

Execute if some condition is true  
`when(BooleanSupplier supplier)` - execute next chain of methods if supplier returned `true`  
`when(Boolean isExecutable)` - execute next chain of methods if parameter is `true`

```java
import static ru.khanin.scenario.Scenario.Static.when;

var result = when(() -> false)
	.step(() -> "Hello")
	.map(String::length)
	.orNull();

assert result == null;
```

Execute some code if step result meets condition  
`.when(Predicate<T> predicate, Consumer<T> consumer)` - execute consumer if predicate returned `true`

```java
var result = step(() -> "Hello")
	.when(it -> "Hello".equals(it),
              it -> System.out.println(it))
	.orNull();

assert result.equals("Hello");
```

### Error handling
`.onError(Class<T> exceptionClass, Consumer<T> consumer)` - handle exception of particular type  
`.onError(Consumer<T> consumer)` - handle any exception if there is one

Let's imagine that we have some specific exception and need to do something depending on error code
```java
var result = step(() -> throwSpecificException())
	.onError(SpecificException.class,
		it -> throw switch(it.getCode()) {
			case "001" -> new ExceptionForCode001();
			case "002" -> new ExceptionForCode002();
			default -> new DefaultException();
	})
	.onError(it -> log.error(it))
	.orNull();
```


### Terminal operations

`.get()` - return step value. Throws IllegalStateException if step result is `null`  
`.orNull()` - return step value or `null`  
`.orElse(T value)` - return step value or offered value  
`.orElse(Supplier<T> supplier)` - return step value or execute supplier function