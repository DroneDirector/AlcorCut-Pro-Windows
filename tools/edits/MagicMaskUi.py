# Windows wording (PC instead of phone).
EDITS = [
    ('if (e == null) { status = "The AI couldn\'t start on this phone" + (Sam.failed?.let { " ($it)" } ?: ""); return@LaunchedEffect }',
     'if (e == null) { status = "The AI couldn\'t start on this PC" + (Sam.failed?.let { " ($it)" } ?: ""); return@LaunchedEffect }'),
    ('Text("The AI follows the object frame by frame (on the phone). It takes a little while — you can keep editing.", color = Pro.Dim, fontSize = 10.sp, modifier = Modifier.padding(top = 6.dp))',
     'Text("The AI follows the object frame by frame (on the PC). It takes a little while — you can keep editing.", color = Pro.Dim, fontSize = 10.sp, modifier = Modifier.padding(top = 6.dp))'),
]
