using CourseSync.Api.Data;
using Microsoft.EntityFrameworkCore.Infrastructure;
using Microsoft.EntityFrameworkCore.Migrations;

#nullable disable

namespace CourseSync.Api.Migrations;

[DbContext(typeof(AppDbContext))]
[Migration("20260325120000_CourseNameMax50")]
public partial class CourseNameMax50 : Migration
{
    protected override void Up(MigrationBuilder migrationBuilder)
    {
        migrationBuilder.AlterColumn<string>(
            name: "name",
            table: "courses",
            type: "character varying(50)",
            maxLength: 50,
            nullable: false,
            oldClrType: typeof(string),
            oldType: "character varying(20)",
            oldMaxLength: 20);
    }

    protected override void Down(MigrationBuilder migrationBuilder)
    {
        migrationBuilder.AlterColumn<string>(
            name: "name",
            table: "courses",
            type: "character varying(20)",
            maxLength: 20,
            nullable: false,
            oldClrType: typeof(string),
            oldType: "character varying(50)",
            oldMaxLength: 50);
    }
}
