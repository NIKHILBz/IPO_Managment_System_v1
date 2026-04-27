package com.ipo.entity.model;

import javax.persistence.Index;

public @interface Table {

    String name();

    Index[] indexes();

}
