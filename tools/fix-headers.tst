regex without named back references
m/.*Copyright \(c\) ((\d{4})\s*[,-]\s*(\d{4})?)\,?(([^\r\<\>\,]*)(\<.*\>)?)\s*/i

 * Copyright (c) 2008, Example Company Inc.
 * Copyright (c) 2008, Joe Developer <joe.dev@example.org>
 * Copyright (c) 2008, 2009 Joe Developer <joe.dev@example.org>
 * Copyright (c) 2005-2009 Joe Developer <joe.dev@example.org>
 * Copyright (c) 2008, 2009 Other Examples Inc.
 * Copyright (c) 2008-2010 Example Company Inc.
 * Copyright (C) 2009-2010, Yet More Examples Ltd.

